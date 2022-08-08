package no.nav.dagpenger.behov.journalforing.tjenester

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.behov.journalforing.fillager.FilURN
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokument
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format
import no.nav.dagpenger.behov.journalforing.soknad.SoknadHttp
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class JournalforingBehovLøser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi,
    private val faktahenter: SoknadHttp
) : River.PacketListener {
    private companion object {
        private val logg = KotlinLogging.logger {}
        private val skipSet = setOf("50a844a6-2458-42c6-bc0d-600bc920c108")
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
        val søknadId = packet["søknad_uuid"].asText()
        val ident = packet["ident"].asText()
        withLoggingContext(
            "søknadId" to søknadId
        ) {
            logg.info("Mottok behov for ny journalpost med uuid $søknadId")
            if (skipSet.contains(søknadId)) return
            runBlocking(MDCContext()) {
                val dokumenter: List<Dokument> = packet["dokumenter"].map { dokument ->
                    Dokument(
                        dokument["brevkode"].asText(),
                        dokument["varianter"].map { variant ->
                            Variant(
                                Filtype.valueOf(variant["type"].asText()),
                                Format.valueOf(variant["format"].asText()),
                                fillager.hentFil(FilURN(variant["urn"].asText()), ident)
                            )
                        }.toMutableList().also {
                            it.add(faktahenter.hentJsonSøknad(søknadId))
                        }
                    )
                }
                sikkerlogg.info { "Oppretter journalpost med $dokumenter" }
                val journalpost = journalpostApi.opprett(
                    ident = ident,
                    dokumenter = dokumenter
                )
                packet["@løsning"] = mapOf(
                    "NyJournalpost" to journalpost.id
                )
                context.publish(packet.toJson())
            }
        }
    }
}
