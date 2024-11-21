package no.nav.dagpenger.behov.journalforing.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.utils.io.core.toByteArray
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Resultat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertContentEquals

internal class RapporteringJournalføringBehovLøserTest {
    private val behov = "JournalføreRapportering"
    private val behovId = "34f6743c-bd9a-4902-ae68-fae0171b1e68"
    private val ident = "01020312345"
    private val periodeId = "periodeId123"
    private val base64EncodedPdf = "UERG"
    private val kanSendesFra = "2024-01-02"
    private val userAgent = "User agent"
    private val frontendSha = "FRONTEND_SHA"
    private val backendSha = "BACKEND_SHA"

    private val journalpostId = "journalpost123"
    private val json = "{\"key1\": \"value1\"}"

    private val journalføreRapportering =
        """
        {
          "@event_name": "behov",
          "@behovId": "$behovId",
          "@behov": [
            "$behov"
          ],
          "meldingsreferanseId":"d0ce2eef-ab53-4b06-acf3-4c85386dc561",
          "ident": "$ident",
          "JournalføreRapportering":{
            "periodeId": "$periodeId",
            "brevkode": "NAV 00-10.02",
            "tittel": "Meldekort for uke 42-43 (14.10.2024 - 27.10.2024) elektronisk mottatt av NAV",
            "json": "{\"key1\": \"value1\"}",
            "pdf": "$base64EncodedPdf",
            "tilleggsopplysninger": [
              { "first": "periodeId", "second": "$periodeId" },
              { "first": "kanSendesFra", "second": "$kanSendesFra" },
              { "first": "userAgent", "second": "$userAgent" },
              { "first": "frontendGithubSha", "second": "$frontendSha" },
              { "first": "backendGithubSha", "second": "$backendSha" }
            ]
          },
          "@id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
          "@opprettet": "2023-10-23T18:53:08.056035121",
          "system_read_count": 0,
          "system_participating_services":[{"id": "30ef9625-196a-445b-9b4e-67e0e6a5118d", "service": "dp-rapportering"}]
        }
        """.trimIndent().replace("\n", "")

    private val journalpostApi = mockk<JournalpostApi>()
    private val testRapid =
        TestRapid().also {
            RapporteringJournalføringBehovLøser(it, journalpostApi)
        }

    @Test
    fun `løser behov for å opprette ny journalpost for rapportering`() {
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        val sendteTilleggsopplysninger = slot<List<Pair<String, String>>>()
        coEvery {
            journalpostApi.opprett(
                eq(ident),
                capture(sendteDokumenter),
                eq(behovId),
                capture(sendteTilleggsopplysninger),
                eq(true),
                any(),
                any(),
            )
        } returns Resultat(journalpostId, true, emptyList(), "Journalpost ferdigstilt")

        testRapid.sendTestMessage(journalføreRapportering)

        assertEquals(1, sendteDokumenter.captured.size)
        with(sendteDokumenter.captured[0]) {
            assertEquals("NAV 00-10.02", this.brevkode)
            assertEquals("Meldekort for uke 42-43 (14.10.2024 - 27.10.2024) elektronisk mottatt av NAV", this.tittel)
            assertEquals(2, this.varianter.size)
            assertEquals(JournalpostApi.Variant.Filtype.JSON, this.varianter[0].filtype)
            assertEquals(JournalpostApi.Variant.Format.ORIGINAL, this.varianter[0].format)
            assertContentEquals(json.toByteArray(), this.varianter[0].fysiskDokument)
            assertEquals(JournalpostApi.Variant.Filtype.PDFA, this.varianter[1].filtype)
            assertEquals(JournalpostApi.Variant.Format.ARKIV, this.varianter[1].format)
            assertContentEquals(Base64.getDecoder().decode(base64EncodedPdf), this.varianter[1].fysiskDokument)
        }

        assertEquals(5, sendteTilleggsopplysninger.captured.size)
        with(sendteTilleggsopplysninger.captured[0]) {
            assertEquals("periodeId", this.first)
            assertEquals(periodeId, this.second)
        }
        with(sendteTilleggsopplysninger.captured[1]) {
            assertEquals("kanSendesFra", this.first)
            assertEquals(kanSendesFra, this.second)
        }
        with(sendteTilleggsopplysninger.captured[2]) {
            assertEquals("userAgent", this.first)
            assertEquals(userAgent, this.second)
        }
        with(sendteTilleggsopplysninger.captured[3]) {
            assertEquals("frontendGithubSha", this.first)
            assertEquals(frontendSha, this.second)
        }
        with(sendteTilleggsopplysninger.captured[4]) {
            assertEquals("backendGithubSha", this.first)
            assertEquals(backendSha, this.second)
        }

        with(testRapid.inspektør) {
            assertEquals(1, size)
            assertEquals(journalpostId, field(0, "@løsning")["JournalføreRapportering"].asText())
        }
    }
}
