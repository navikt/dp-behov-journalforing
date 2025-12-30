package no.nav.dagpenger.behov.journalforing.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.behov.journalforing.fillager.FilURN
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp
import java.util.Base64

internal class VedtaksbrevJournalføringBehovløser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi,
) : River.PacketListener {
    companion object {
        const val BEHOV_NAVN = "JournalføringBehov"
        private val behovIdSkipSet = setOf("fb4a8b58-3984-431a-811b-ab35b50c0e12")

        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
        val rapidFilter: River.() -> Unit = {
            precondition {
                it.requireValue("@event_name", "behov")
                it.requireAll("@behov", listOf(BEHOV_NAVN))
                it.forbid("@løsning")
            }
            validate { it.requireKey("ident", "sak", "pdfUrn", "@behovId") }
            validate { it.interestedIn("skjemaKode", "tittel") }
        }
    }

    init {
        River(rapidsConnection).apply(rapidFilter).register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val filUrn: FilURN = packet.filUrn()
        val ident = packet.ident()
        val tittel = packet.tittel() ?: "Vedtak om dagpenger"
        val behovId = packet.behovId()
        val sak = packet.sak()
        val skjemaKode = packet.skjemaKode() ?: "NAV-DAGPENGER-VEDTAK"

        withLoggingContext(
            "behovId" to behovId,
        ) {
            logger.info { "Behandler behov for journalføring av vedtaksbrev med id $behovId" }

            if (behovIdSkipSet.contains(behovId)) return

            runBlocking {
                val fil = fillager.hentFil(filUrn, eier = ident)

                runCatching {
                    val resultat =
                        journalpostApi.opprett(
                            forsøkFerdigstill = true,
                            journalpost =
                                JournalpostApiHttp.Journalpost(
                                    journalposttype = "UTGAAENDE",
                                    avsenderMottaker =
                                        JournalpostApiHttp.Journalpost.Bruker(
                                            id = ident,
                                            idType = "FNR",
                                        ),
                                    bruker =
                                        JournalpostApiHttp.Journalpost.Bruker(
                                            id = ident,
                                            idType = "FNR",
                                        ),
                                    tema = "DAG",
                                    kanal = "NAV_NO",
                                    // TODO Vi må finne ut om vi trenger NAY-enhetene
                                    journalfoerendeEnhet = "9999",
                                    tittel = tittel,
                                    eksternReferanseId = behovId,
                                    tilleggsopplysninger = emptyList(),
                                    sak = sak,
                                    dokumenter =
                                        listOf(
                                            JournalpostApiHttp.Dokument(
                                                brevkode = skjemaKode,
                                                tittel = tittel,
                                                dokumentvarianter =
                                                    listOf(
                                                        JournalpostApiHttp.Dokumentvariant(
                                                            filtype = JournalpostApiHttp.Dokumentvariant.Filtype.PDFA,
                                                            variantformat = JournalpostApiHttp.Dokumentvariant.Variant.ARKIV,
                                                            fysiskDokument = Base64.getEncoder().encodeToString(fil),
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
                        )

                    if (!resultat.journalpostferdigstilt) {
                        sikkerlogg.error {
                            "Journalposten ble ikke ferdigstilt. Resultat fra Joark: $resultat for pakke $packet"
                        }
                        throw JournalpostIkkeFerdigstiltException()
                    } else {
                        packet["@løsning"] =
                            mapOf(
                                BEHOV_NAVN to
                                    mapOf(
                                        "journalpostId" to resultat.journalpostId,
                                    ),
                            )

                        val message = packet.toJson()
                        context.publish(message)
                        sikkerlogg.info {
                            "Sendt ut løsning $message"
                        }
                    }
                }.onFailure {
                    logger.error(it) {
                        "Feil ved journalføring av dokument: ${it.message}"
                    }

                    sikkerlogg.error(it) {
                        "Feil ved journalføring av dokument: ${it.message} for pakke: ${packet.toJson()}"
                    }
                    throw it
                }
            }
        }
    }

    internal class JournalpostIkkeFerdigstiltException : RuntimeException()

    private fun JsonMessage.sak(): JournalpostApiHttp.Sak {
        val fagsystem =
            when (this["sak"]["kontekst"].asText()) {
                "Arena" -> "AO01"
                else -> "DAGPENGER"
            }

        return JournalpostApiHttp.Sak(fagsakId = this["sak"]["id"].asText(), fagsakSystem = fagsystem)
    }

    private fun JsonMessage.tittel(): String? {
        val tittelNode = this.get("tittel")
        return when (tittelNode.isMissingOrNull()) {
            true -> null
            false -> tittelNode.asText()
        }
    }

    private fun JsonMessage.skjemaKode(): String? {
        val tittelNode = this.get("skjemaKode")
        return when (tittelNode.isMissingOrNull()) {
            true -> null
            false -> tittelNode.asText()
        }
    }

    private fun JsonMessage.ident(): String = this["ident"].asText()

    private fun JsonMessage.filUrn(): FilURN = FilURN(urn = this["pdfUrn"].asText())

    private fun JsonMessage.behovId(): String = this["@behovId"].asText()
}
