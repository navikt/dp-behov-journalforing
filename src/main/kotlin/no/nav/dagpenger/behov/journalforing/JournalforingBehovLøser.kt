package no.nav.dagpenger.behov.journalforing

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River


internal class JournalforingBehovLøser(
    rapidsConnection: RapidsConnection
) : River.PacketListener {
    private companion object {
        private val logg = KotlinLogging.logger {}
        const val BEHOV = "arkiverbarSøknad"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(BEHOV)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("søknad_uuid", "ident") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("Mottok behov for søknadspdf med uuid ${packet["søknad_uuid"].asText()}")
        packet["@løsning"] = mapOf(BEHOV to "urn:dokument:wattevs")
        context.publish(packet.toJson())
    }
}
