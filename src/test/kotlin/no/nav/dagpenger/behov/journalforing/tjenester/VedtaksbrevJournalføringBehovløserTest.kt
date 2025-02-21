package no.nav.dagpenger.behov.journalforing.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.json.shouldEqualSpecifiedJsonIgnoringOrder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.behov.journalforing.fillager.FilURN
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp
import no.nav.dagpenger.behov.journalforing.tjenester.VedtaksbrevJournalføringBehovløser.JournalpostIkkeFerdigstiltException
import org.junit.jupiter.api.Test

class VedtaksbrevJournalføringBehovløserTest {
    private val testRapid = TestRapid()
    private val testIdent = "01010199999"
    private val pdfUrnString = "urn:disney:mikkemus"
    private val sakId = "sak1"
    private val sakKontekst = "sakKontekst"
    private val behovId = "behovId"
    private val journalpostId = "journalpostId"

    @Test
    fun `Skal løse behov om journalføring`() {
        val fillagerMock =
            mockk<Fillager>().also {
                coEvery { it.hentFil(any(), any()) } returns ByteArray(0)
            }
        val journalpostApiMock =
            mockk<JournalpostApi>().also {
                coEvery { it.opprett(true, any()) } returns
                    JournalpostApiHttp.Resultat(
                        journalpostId = journalpostId,
                        journalpostferdigstilt = true,
                        dokumenter = listOf(),
                        melding = "Journalpost ferdigstilt",
                    )
            }
        VedtaksbrevJournalføringBehovløser(
            rapidsConnection = testRapid,
            fillager = fillagerMock,
            journalpostApi = journalpostApiMock,
        )

        testRapid.sendTestMessage(
            testMelding(
                ident = testIdent,
                pdfUrnString = pdfUrnString,
                sakId = sakId,
                sakKontekst = sakKontekst,
                behovId = behovId,
            ),
        )
        coVerify(exactly = 1) {
            fillagerMock.hentFil(FilURN(pdfUrnString), testIdent)
        }
        coVerify(exactly = 1) {
            journalpostApiMock.opprett(true, any())
        }

        testRapid.inspektør.size shouldBe 1
        testRapid.inspektør.message(0).toString() shouldEqualSpecifiedJsonIgnoringOrder
            """
            {
              "@løsning":
                 {
                   "JournalføringBehov":
                   {
                     "journalpostId": "journalpostId"
                   }
                }
            }     
            """.trimIndent()
    }

    @Test
    fun `Kaster exception hvis vi ikke får ferdigstillt journalføring`() {
        val fillagerMock =
            mockk<Fillager>().also {
                coEvery { it.hentFil(any(), any()) } returns ByteArray(0)
            }
        val journalpostApiMock =
            mockk<JournalpostApi>().also {
                coEvery { it.opprett(true, any()) } returns
                    JournalpostApiHttp.Resultat(
                        journalpostId = journalpostId,
                        journalpostferdigstilt = false,
                        dokumenter = listOf(),
                        melding = "Journalpost ikke ferdigstilt",
                    )
            }
        shouldThrow<JournalpostIkkeFerdigstiltException> {
            VedtaksbrevJournalføringBehovløser(
                rapidsConnection = testRapid,
                fillager = fillagerMock,
                journalpostApi = journalpostApiMock,
            )

            testRapid.sendTestMessage(
                testMelding(
                    ident = testIdent,
                    pdfUrnString = pdfUrnString,
                    sakId = sakId,
                    sakKontekst = sakKontekst,
                    behovId = behovId,
                ),
            )
        }
    }

    private fun testMelding(
        ident: String,
        pdfUrnString: String,
        sakId: String,
        sakKontekst: String,
        behovId: String,
    ) = //language=JSON
        """
        {
          "@event_name": "behov",
          "@behov": ["JournalføringBehov"],
          "@behovId": "$behovId",
          "ident": "$ident",
          "pdfUrn": "$pdfUrnString",
            "sak": {
                "id": "$sakId",
                "kontekst": "$sakKontekst"
            }
        }
        """.trimIndent()
}
