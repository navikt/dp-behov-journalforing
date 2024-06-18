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

internal class GenerellJournalføringBehovløser(
    rapidsConnection: RapidsConnection,
    private val fillager: Fillager,
    private val journalpostApi: JournalpostApi
) : River.PacketListener {
    companion object {
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
        const val BEHOV_NAVN = "JournalføringBehov"
        val rapidFilter: River.() -> Unit = {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf(BEHOV_NAVN)) }
            validate { it.requireKey("ident", "sak", "pdfUrn", "@behovId") }
            validate { it.interestedIn("skjemaKode", "tittel") }
            validate { it.rejectKey("@løsning") }
        }
        internal const val NY_JOURNAL_POST = "NyJournalpost"
    }

    init {
        River(rapidsConnection).apply(rapidFilter).register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val filUrn: FilURN = packet.filUrn()
        val ident = packet.ident()
        val tittel = packet.tittel() ?: "Dagpenger vedtak"
        val behovId = packet.behovId()

        runBlocking {
            val fil = fillager.hentFil(filUrn, eier = ident)
            journalpostApi.opprett(
                payload = JournalpostApiHttp.JournalpostPayload(
                    journalposttype = "UTGAAENDE",
                    avsenderMottaker = JournalpostApiHttp.JournalpostPayload.Bruker(
                        id = ident,
                        idType = "FNR"
                    ),
                    bruker = JournalpostApiHttp.JournalpostPayload.Bruker(
                        id = ident,
                        idType = "FNR"
                    ),
                    tema = "DAG",
                    kanal = "NAV_NO",
                    journalfoerendeEnhet = "9999", //todo finne ut ref Mona
                    tittel = tittel,
                    dokumenter = listOf(
                        JournalpostApiHttp.Dokument(
                            brevkode = null,
                            dokumentvarianter = listOf(),
                            tittel = null

                        )
                    ),
                    eksternReferanseId = behovId,
                    tilleggsopplysninger = emptyList(),
                    sak = JournalpostApiHttp.Sak(
                        fagsakId = packet["sak"]["fagsakId"].asText(),
                        fagsaksystem = packet["sak"]["fagsaksystem"].asText()

                    )
                ),
            )
//                .let { journalpost -> //                packet["@løsning"] =
//                    mapOf(
//                        NY_JOURNAL_POST to journalpost.id,
//                    )
//                val message = packet.toJson()
//                context.publish(message)
//                sikkerlogg.info { "Sendt ut løsning $message"
        }
    }

}

private fun JsonMessage.tittel(): String? {
    val tittelNode = this.get("tittel")
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