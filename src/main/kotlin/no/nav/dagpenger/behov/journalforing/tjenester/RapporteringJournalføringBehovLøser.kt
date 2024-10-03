package no.nav.dagpenger.behov.journalforing.tjenester

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokument
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.Base64

internal class RapporteringJournalføringBehovLøser(
    rapidsConnection: RapidsConnection,
    private val journalpostApi: JournalpostApi,
) : River.PacketListener {
    internal companion object {
        private val logg = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.RapporteringJournalføringBehovLøser")
        internal const val BEHOV = "JournalføreRapportering"
    }

    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("@event_name", "behov") }
                validate { it.demandAll("@behov", listOf(BEHOV)) }
                validate { it.rejectKey("@løsning") }
                validate { it.requireKey("@behovId", "ident") }
                validate {
                    it.require(BEHOV) { behov ->
                        behov.required("periodeId")
                        behov.required("brevkode")
                        behov.required("json")
                        behov.required("pdf")
                        behov.required("tilleggsopplysninger")
                    }
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val ident = packet["ident"].asText()
        val periodeId = packet[BEHOV]["periodeId"].asText()
        val behovId = packet["@behovId"].asText()

        withLoggingContext(
            "periodeId" to periodeId,
            "behovId" to behovId,
        ) {
            try {
                logg.info("Mottok behov for ny journalpost for periode med id $periodeId")
                runBlocking(MDCContext()) {
                    val brevkode = packet[BEHOV]["brevkode"].asText()
                    val json = packet[BEHOV]["json"].asText()
                    val pdf = packet[BEHOV]["pdf"].asText()

                    val tilleggsopplysninger: List<Pair<String, String>> =
                        jacksonObjectMapper().convertValue(
                            packet[BEHOV]["tilleggsopplysninger"],
                            object : TypeReference<List<Pair<String, String>>>() {},
                        )

                    val dokumenter: List<Dokument> =
                        listOf(
                            opprettDokument(brevkode, json.encodeToByteArray(), Base64.getDecoder().decode(pdf)),
                        )

                    sikkerlogg.info { "Oppretter journalpost med $dokumenter" }
                    sikkerlogg.info { "Oppretter journalost basert på ${packet.toJson()}" }

                    val journalpost =
                        journalpostApi.opprett(
                            ident = ident,
                            dokumenter = dokumenter,
                            eksternReferanseId = behovId,
                            tilleggsopplysninger = tilleggsopplysninger,
                        )
                    packet["@løsning"] =
                        mapOf(
                            "journalpostId" to journalpost.id,
                        )
                    context.publish(packet.toJson())
                    logg.info { "Løser behov $BEHOV med journalpostId=${journalpost.id}" }
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.InternalServerError) {
                    sikkerlogg.warn(e) { "Feilet for '$ident'. Hvis dette er i dev, forsøk å importer identen på nytt i Dolly." }
                }
                throw e
            }
        }
    }

    private fun opprettDokument(
        brevkode: String,
        json: ByteArray,
        pdf: ByteArray,
    ): Dokument =
        Dokument(
            brevkode = brevkode,
            tittel = DokumentTittelOppslag.hentTittel(brevkode),
            varianter =
                listOf(
                    Variant(
                        filtype = Filtype.JSON,
                        format = Format.ORIGINAL,
                        fysiskDokument = json,
                    ),
                    Variant(
                        filtype = Filtype.PDFA,
                        format = Format.ARKIV,
                        fysiskDokument = pdf,
                    ),
                ),
        )
}
