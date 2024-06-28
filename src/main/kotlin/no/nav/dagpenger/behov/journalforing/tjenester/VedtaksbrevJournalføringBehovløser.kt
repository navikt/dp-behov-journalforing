package no.nav.dagpenger.behov.journalforing.tjenester

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.behov.journalforing.fillager.FilURN
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.util.Base64

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class VedtaksbrevJournalføringBehovløser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi,
) : River.PacketListener {
    companion object {
        const val BEHOV_NAVN = "JournalføringBehov"
        val rapidFilter: River.() -> Unit = {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf(BEHOV_NAVN)) }
            validate { it.requireKey("ident", "sak", "pdfUrn", "@behovId") }
            validate { it.interestedIn("skjemaKode", "tittel") }
            validate { it.rejectKey("@løsning") }
        }
    }

    init {
        River(rapidsConnection).apply(rapidFilter).register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val filUrn: FilURN = packet.filUrn()
        val ident = packet.ident()
        val tittel = packet.tittel() ?: "Dagpenger vedtak"
        val behovId = packet.behovId()
        val sak = packet.sak()
        val skjemaKode = packet.skjemaKode() ?: "NAV-DAGPENGER-VEDTAK"

        runBlocking {
            val fil = fillager.hentFil(filUrn, eier = ident)

            runCatching {
                val resultat =
                    journalpostApi.opprett(
                        forsøkFerdigstill = true,
                        payload =
                            JournalpostApiHttp.JournalpostPayload(
                                journalposttype = "UTGAAENDE",
                                avsenderMottaker =
                                    JournalpostApiHttp.JournalpostPayload.Bruker(
                                        id = ident,
                                        idType = "FNR",
                                    ),
                                bruker =
                                    JournalpostApiHttp.JournalpostPayload.Bruker(
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

    internal class JournalpostIkkeFerdigstiltException() :
        RuntimeException()

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

    private fun JsonMessage.ident(): String {
        return this["ident"].asText()
    }

    private fun JsonMessage.filUrn(): FilURN {
        return FilURN(urn = this["pdfUrn"].asText())
    }

    private fun JsonMessage.behovId(): String {
        return this["@behovId"].asText()
    }
}
