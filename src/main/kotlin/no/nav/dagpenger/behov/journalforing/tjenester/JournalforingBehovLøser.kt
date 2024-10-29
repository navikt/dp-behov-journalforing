package no.nav.dagpenger.behov.journalforing.tjenester

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
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
import kotlin.math.log10
import kotlin.math.pow

internal class JournalforingBehovLøser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi,
    private val faktahenter: SoknadHttp,
) : River.PacketListener {
    internal companion object {
        private val logg = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.JournalforingBehovLøser")
        private val behovIdSkipSet = setOf("99c359cf-cb3a-4986-8934-c13aadc64901")
        internal const val NY_JOURNAL_POST = "NyJournalpost"
    }

    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandAll("@behov", listOf(NY_JOURNAL_POST)) }
                validate { it.rejectKey("@løsning") }
                validate { it.requireKey("@behovId", "søknad_uuid", "ident", "type", NY_JOURNAL_POST) }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val søknadId = packet["søknad_uuid"].asText()
        val ident = packet["ident"].asText()
        val behovId = packet["@behovId"].asText()
        val innsendingType = InnsendingType.valueOf(packet["type"].asText())

        withLoggingContext(
            "søknadId" to søknadId,
            "behovId" to behovId,
        ) {
            logg.info(
                "Mottok behov for ny journalpost med uuid=$søknadId, pakkestørrelse=${
                    prettyPrintFileSize(packet.toJson().length.toLong())
                }",
            )
            if (behovIdSkipSet.contains(behovId)) return
            runBlocking(MDCContext()) {
                val hovedDokument =
                    packet[NY_JOURNAL_POST]["hovedDokument"].let { jsonNode ->
                        val brevkode =
                            when (jsonNode.skjemakode()) {
                                "GENERELL_INNSENDING" -> jsonNode.skjemakode()
                                else -> innsendingType.brevkode(jsonNode.skjemakode())
                            }
                        val dokument = jsonNode.toDokument(ident, brevkode)
                        dokument.copy(varianter = dokument.varianter + faktahenter.hentJsonSøknad(søknadId))
                    }
                val dokumenter: List<Dokument> =
                    listOf(hovedDokument) + packet[NY_JOURNAL_POST]["dokumenter"].map { it.toDokument(ident) }

                sikkerlogg.info { "Oppretter journalpost med $dokumenter" }
                sikkerlogg.info { "Oppretter journalost basert på ${packet.toJson()}" }
                try {
                    journalpostApi
                        .opprett(
                            ident = ident,
                            dokumenter = dokumenter,
                            eksternReferanseId = behovId,
                        forsøkFerdigstill = true,
                        ).let { resultat ->
                            packet["@løsning"] =
                                mapOf(
                                    NY_JOURNAL_POST to resultat.journalpostId,
                                )
                            val message = packet.toJson()
                            context.publish(message)
                            sikkerlogg.info { "Sendt ut løsning $message" }
                        }
                } catch (e: ClientRequestException) {
                    when (e.response.status) {
                        HttpStatusCode.InternalServerError, HttpStatusCode.NotFound ->
                            sikkerlogg.warn(e) {
                                "Feilet for '$ident'. Hvis dette er i dev, forsøk å importere identen på nytt i Dolly."
                            }
                        else -> sikkerlogg.error(e) { "Opprettelse av  journalpost med $dokumenter feilet" }
                    }
                    throw e
                }
            }
        }
    }

    private enum class InnsendingType {
        NY_DIALOG,
        ETTERSENDING_TIL_DIALOG,
        ;

        fun brevkode(skjemakode: String) =
            when (this) {
                NY_DIALOG -> "NAV $skjemakode"
                ETTERSENDING_TIL_DIALOG -> "NAVe $skjemakode"
            }
    }

    private suspend fun JsonNode.toDokument(
        ident: String,
        brevkode: String = this.skjemakode(),
    ) = Dokument(
        brevkode = brevkode,
        tittel = DokumentTittelOppslag.hentTittel(brevkode),
        varianter =
            this["varianter"].map { variant ->
                val fysiskDokument: ByteArray = fillager.hentFil(FilURN(variant["urn"].asText()), ident)
                Variant(
                    filtype = Filtype.valueOf(variant["type"].asText()),
                    format = Format.valueOf(variant["variant"].asText()),
                    fysiskDokument = fysiskDokument,
                )
            },
    )

    private fun JsonNode.skjemakode(): String = this["skjemakode"].asText()
}

fun prettyPrintFileSize(size: Long): String {
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
