package no.nav.dagpenger.behov.journalforing.tjenester

import com.fasterxml.jackson.databind.JsonNode
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
    internal companion object {
        private val logg = KotlinLogging.logger {}
        private val skipSet = setOf(
            "50a844a6-2458-42c6-bc0d-600bc920c108",
            "f6224540-c224-4631-8e15-e43e03d53a0e",
            "f3895258-336c-4d2d-94cc-343a07792d24",
            "35d3bedb-5dfb-41d3-aabf-2bc4626de484",
            "a94b9257-7b9a-4192-89fc-40ba4589c16f"
        )
        internal const val NY_JOURNAL_POST = "NyJournalpost"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(NY_JOURNAL_POST)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@behovId", "søknad_uuid", "ident", "type", NY_JOURNAL_POST) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet["søknad_uuid"].asText()
        val ident = packet["ident"].asText()
        val behovId = packet["@behovId"].asText()
        val innsendingType = InnsendingType.valueOf(packet["type"].asText())

        withLoggingContext(
            "søknadId" to søknadId,
            "behovId" to behovId
        ) {
            logg.info("Mottok behov for ny journalpost med uuid $søknadId")
            if (skipSet.contains(søknadId)) return
            runBlocking(MDCContext()) {
                val hovedDokument = packet[NY_JOURNAL_POST]["hovedDokument"].let { jsonNode ->
                    val brevkode = when (jsonNode.skjemakode()) {
                        "GENERELL_INNSENDING" -> jsonNode.skjemakode()
                        else -> innsendingType.brevkode(jsonNode.skjemakode())
                    }
                    val dokument = jsonNode.toDokument(ident, brevkode)
                    dokument.copy(varianter = dokument.varianter + faktahenter.hentJsonSøknad(søknadId))
                }
                val dokumenter: List<Dokument> =
                    listOf(hovedDokument) + packet[NY_JOURNAL_POST]["dokumenter"].map { it.toDokument(ident) }

                sikkerlogg.info { "Oppretter journalpost med $dokumenter" }
                val journalpost = journalpostApi.opprett(
                    ident = ident,
                    dokumenter = dokumenter,
                    eksternReferanseId = behovId
                )
                packet["@løsning"] = mapOf(
                    NY_JOURNAL_POST to journalpost.id
                )
                context.publish(packet.toJson())
                logg.info { "Løser behov $NY_JOURNAL_POST med journalpostId=${journalpost.id}" }
            }
        }
    }

    private enum class InnsendingType {
        NY_DIALOG, ETTERSENDING_TIL_DIALOG;

        fun brevkode(skjemakode: String) = when (this) {
            NY_DIALOG -> "NAV $skjemakode"
            ETTERSENDING_TIL_DIALOG -> "NAVe $skjemakode"
        }
    }

    private suspend fun JsonNode.toDokument(ident: String, brevkode: String = this.skjemakode()) =
        Dokument(
            brevkode = brevkode,
            tittel = DokumentTittelOppslag.hentTittel(brevkode),
            varianter = this["varianter"].map { variant ->
                Variant(
                    filtype = Filtype.valueOf(variant["type"].asText()),
                    format = Format.valueOf(variant["variant"].asText()),
                    fysiskDokument = fillager.hentFil(FilURN(variant["urn"].asText()), ident)
                )
            }
        )

    private fun JsonNode.skjemakode(): String = this["skjemakode"].asText()
}
