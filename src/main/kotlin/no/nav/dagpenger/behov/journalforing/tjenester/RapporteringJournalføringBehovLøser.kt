package no.nav.dagpenger.behov.journalforing.tjenester

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
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class RapporteringJournalføringBehovLøser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi
) : River.PacketListener {
    internal companion object {
        private val logg = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
        internal const val BEHOV = "JournalføreRapportering"
        internal const val BREVKODE = "M6" // Timelister
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf(BEHOV)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@behovId", "ident") }
            validate {
                it.require(BEHOV) { behov ->
                    behov.required("periodeId")
                    behov.required("json")
                    behov.required("urn")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet["ident"].asText()
        val periodeId = packet[BEHOV]["periodeId"].asText()
        val behovId = packet["@behovId"].asText()

        withLoggingContext(
            "periodeId" to periodeId,
            "behovId" to behovId
        ) {
            try {
                logg.info("Mottok behov for ny journalpost for periode med id $periodeId")
                runBlocking(MDCContext()) {
                    val json = packet[BEHOV]["json"].asText()
                    val urn = packet[BEHOV]["urn"].asText()
                    val dokumenter: List<Dokument> = listOf(
                        opprettDokument(json.encodeToByteArray(), fillager.hentFil(FilURN(urn), ident))
                    )
                    sikkerlogg.info { "Oppretter journalpost med $dokumenter" }
                    sikkerlogg.info { "Oppretter journalost basert på ${packet.toJson()}" }
                    val journalpost = journalpostApi.opprett(
                        ident = ident,
                        dokumenter = dokumenter,
                        eksternReferanseId = behovId,
                        tilleggsopplysninger = listOf(Pair("periodeId", periodeId))
                    )
                    packet["@løsning"] = mapOf(
                        "journalpostId" to journalpost.id
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

    private fun opprettDokument(json: ByteArray, pdf: ByteArray): Dokument {
        return Dokument(
            brevkode = BREVKODE,
            tittel = DokumentTittelOppslag.hentTittel(BREVKODE),
            varianter = listOf(
                Variant(
                    filtype = Filtype.JSON,
                    format = Format.ORIGINAL,
                    fysiskDokument = json
                ),
                Variant(
                    filtype = Filtype.PDFA,
                    format = Format.ARKIV,
                    fysiskDokument = pdf
                )
            )
        )
    }
}
