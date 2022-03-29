package no.nav.dagpenger.behov.journalforing.tjenester

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokumentvariant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokumentvariant.Variant
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class JournalforingBehovLøser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi
) : River.PacketListener {
    private companion object {
        private val logg = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf("NyJournalpost")) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("søknad_uuid", "ident") }
            validate {
                it.requireArray("filer") {
                    requireKey("urn")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            logg.info("Mottok behov for ny journalpost med uuid ${packet["søknad_uuid"].asText()}")
            val dokumenter = packet["filer"].map {
                JournalpostApi.Dokument(
                    "123",
                    listOf(
                        JournalpostApi.Dokumentvariant(
                            Filtype.PDF,
                            Variant.ARKIV,
                            fillager.hentFil(it["urn"].asText()),
                        )
                    )
                )
            }
            val journalpost = journalpostApi.opprett(
                ident = packet["ident"].asText(), dokumenter = dokumenter
            )
            packet["@løsning"] = mapOf(
                "NyJournalpost" to journalpost
            )
            context.publish(packet.toJson())
        }
    }
}
