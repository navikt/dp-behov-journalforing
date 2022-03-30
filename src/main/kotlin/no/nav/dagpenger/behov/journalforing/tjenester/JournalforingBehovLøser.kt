package no.nav.dagpenger.behov.journalforing.tjenester

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokument
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format
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
                it.requireArray("dokumenter") {
                    requireKey("brevkode")
                    requireArray("varianter") {
                        requireKey("type", "urn", "format")
                    }
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            logg.info("Mottok behov for ny journalpost med uuid ${packet["søknad_uuid"].asText()}")
            val dokumenter = packet["dokumenter"].map { dokument ->
                Dokument(
                    dokument["brevkode"].asText(),
                    dokument["varianter"].map { variant ->
                        Variant(
                            Filtype.valueOf(variant["type"].asText()),
                            Format.valueOf(variant["format"].asText()),
                            fillager.hentFil(variant["urn"].asText()),
                        )
                    }
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
