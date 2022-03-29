package no.nav.dagpenger.behov.journalforing

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class JournalforingBehovLoser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi
) : River.PacketListener {
    private companion object {
        private val logg = KotlinLogging.logger {}
        const val BEHOV = "NyJournalpost"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(BEHOV)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("søknad_uuid", "ident") }
            validate {
                it.requireArray("filer") {
                    requireKey("type", "urn")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("Mottok behov for ny journalpost med uuid ${packet["søknad_uuid"].asText()}")
        val filer = packet["filer"].map {
            mapOf(
                "filtype" to it["type"].asText(),
                "fysiskDokument" to fillager.hentFil(it["urn"].asText()),
                "variantformat" to "ARKIV"
            )
        }
        val journalpostId = journalpostApi.opprett(
            ident = packet["ident"].asText(),
            dokumenter = filer
        )
        packet["@løsning"] = mapOf(BEHOV to journalpostId)
        context.publish(packet.toJson())
    }
}
