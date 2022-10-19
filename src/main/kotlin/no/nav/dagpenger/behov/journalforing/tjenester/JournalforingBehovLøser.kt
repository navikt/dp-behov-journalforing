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
        private val skipSet = setOf("50a844a6-2458-42c6-bc0d-600bc920c108", "f6224540-c224-4631-8e15-e43e03d53a0e")
        internal const val NY_JOURNAL_POST = "NyJournalpost"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(NY_JOURNAL_POST)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("søknad_uuid", "ident", NY_JOURNAL_POST) }
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
                val hovedDokument = packet[NY_JOURNAL_POST]["hovedDokument"].let { jsonNode ->
                    val dokument = jsonNode.toDokument(ident)
                    dokument.copy(varianter = dokument.varianter + faktahenter.hentJsonSøknad(søknadId))
                }
                val dokumenter: List<Dokument> =
                    listOf(hovedDokument) + packet[NY_JOURNAL_POST]["dokumenter"].map { it.toDokument(ident) }

                sikkerlogg.info { "Oppretter journalpost med $dokumenter" }
                val journalpost = journalpostApi.opprett(
                    ident = ident,
                    dokumenter = dokumenter
                )
                packet["@løsning"] = mapOf(
                    NY_JOURNAL_POST to journalpost.id
                )
                context.publish(packet.toJson())
            }
        }
    }

    private suspend fun JsonNode.toDokument(ident: String) = Dokument(
        brevkode = this["brevkode"]?.asText(),
        tittel = this["tittel"]?.asText(),
        varianter = this["varianter"].map { variant ->
            Variant(
                filtype = Filtype.valueOf(variant["type"].asText()),
                format = Format.valueOf(variant["variant"].asText()),
                fysiskDokument = fillager.hentFil(FilURN(variant["urn"].asText()), ident)
            )
        }
    )
}
