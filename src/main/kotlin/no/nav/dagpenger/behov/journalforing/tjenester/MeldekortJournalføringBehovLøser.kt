package no.nav.dagpenger.behov.journalforing.tjenester

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokument
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp
import java.util.Base64

internal class MeldekortJournalføringBehovLøser(
    rapidsConnection: RapidsConnection,
    private val journalpostApi: JournalpostApi,
) : River.PacketListener {
    internal companion object {
        private val logg = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
        internal const val BEHOV = "JournalføreMeldekort"
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAll("@behov", listOf(BEHOV))
                    it.forbid("@løsning")
                }
                validate { it.requireKey("@behovId", "ident") }
                validate {
                    it.require(BEHOV) { behov ->
                        behov.required("meldekortId")
                        behov.required("sakId")
                        behov.required("brevkode")
                        behov.required("tittel")
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
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val ident = packet["ident"].asText()
        val meldekortId = packet[BEHOV]["meldekortId"].asText()
        val sakId = packet[BEHOV]["sakId"].asText()
        val behovId = packet["@behovId"].asText()

        withLoggingContext(
            "meldekortId" to meldekortId,
            "behovId" to behovId,
        ) {
            try {
                logg.info { "Mottok behov for ny journalpost for meldekort med id $meldekortId" }
                runBlocking(MDCContext()) {
                    val brevkode = packet[BEHOV]["brevkode"].asText()
                    val tittel = packet[BEHOV]["tittel"].asText()
                    val json = packet[BEHOV]["json"].asText()
                    val pdf = packet[BEHOV]["pdf"].asText()

                    val tilleggsopplysninger: List<Pair<String, String>> =
                        jacksonObjectMapper().convertValue(
                            packet[BEHOV]["tilleggsopplysninger"],
                            object : TypeReference<List<Pair<String, String>>>() {},
                        )

                    val dokumenter: List<Dokument> =
                        listOf(
                            opprettDokument(
                                brevkode,
                                tittel,
                                json.encodeToByteArray(),
                                Base64.getDecoder().decode(pdf),
                            ),
                        )

                    sikkerlogg.info { "Oppretter journalpost med $dokumenter" }
                    sikkerlogg.info { "Oppretter journalpost basert på ${packet.toJson()}" }

                    val resultat =
                        journalpostApi.opprett(
                            ident = ident,
                            dokumenter = dokumenter,
                            eksternReferanseId = behovId,
                            tilleggsopplysninger = tilleggsopplysninger,
                            forsøkFerdigstill = true,
                            tittel = tittel,
                            sak =
                                JournalpostApiHttp.Sak(
                                    sakstype = "FAGSAK",
                                    fagsakId = sakId,
                                    fagsakSystem = "DAGPENGER",
                                ),
                        )
                    packet["@løsning"] =
                        mapOf(
                            BEHOV to resultat.journalpostId,
                        )
                    context.publish(packet.toJson())
                    logg.info { "Løser behov $BEHOV med journalpostId=${resultat.journalpostId}" }
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    sikkerlogg.warn(e) { "Feilet for '$ident'. Hvis dette er i dev, forsøk å importer identen på nytt i Dolly." }
                }

                throw e
            }
        }
    }

    private fun opprettDokument(
        brevkode: String,
        tittel: String,
        json: ByteArray,
        pdf: ByteArray,
    ): Dokument =
        Dokument(
            brevkode = brevkode,
            tittel = tittel,
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
