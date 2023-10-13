package no.nav.dagpenger.behov.journalforing.tjenester

import io.ktor.utils.io.core.toByteArray
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Journalpost
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

internal class RapporteringJournalføringBehovLøserTest {
    private val behovId = "34f6743c-bd9a-4902-ae68-fae0171b1e68"
    private val ident = "01020312345"
    private val periodeId = "periodeId123"
    private val journalpostId = "journalpost123"
    private val json = "{\"key1\": \"value1\"}"
    private val pdf = "PdfGenerertFraJSON"

    private val journalføreRapportering = """
    {
      "@event_name": "behov",
      "@behovId": "$behovId",
      "@behov": [
        "JournalføreRapportering"
      ],
      "ident": "$ident",
      "periodeId": "$periodeId",
      "JournalføreRapportering": {
        "json": "{\"key1\": \"value1\"}",
        "urn": "urn:vedlegg:periodeId/netto.pdf"
      },
      "@id": "c1116672-9057-406b-93e1-7198f7282126",
      "@opprettet": "2022-09-26T09:47:25.411111",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "id": "c1116672-9057-406b-93e1-7198f7282126",
          "time": "2022-09-26T09:47:25.411111"
        }
      ]
    }
    """.trimIndent()

    private val fillager = mockk<Fillager>().also {
        coEvery {
            it.hentFil(any(), eq(ident))
        } returns pdf.toByteArray()
    }
    private val journalpostApi = mockk<JournalpostApi>()
    private val testRapid = TestRapid().also {
        RapporteringJournalføringBehovLøser(it, fillager, journalpostApi)
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
                capture(sendteTilleggsopplysninger)
            )
        } returns Journalpost(journalpostId)

        testRapid.sendTestMessage(journalføreRapportering)

        assertEquals(1, sendteDokumenter.captured.size)
        with(sendteDokumenter.captured[0]) {
            assertEquals("M6", this.brevkode)
            assertEquals("Timelister", this.tittel)
            assertEquals(2, this.varianter.size)
            assertEquals(JournalpostApi.Variant.Filtype.JSON, this.varianter[0].filtype)
            assertEquals(JournalpostApi.Variant.Format.ORIGINAL, this.varianter[0].format)
            assertContentEquals(json.toByteArray(), this.varianter[0].fysiskDokument)
            assertEquals(JournalpostApi.Variant.Filtype.PDFA, this.varianter[1].filtype)
            assertEquals(JournalpostApi.Variant.Format.ARKIV, this.varianter[1].format)
            assertContentEquals(pdf.toByteArray(), this.varianter[1].fysiskDokument)
        }

        assertEquals(1, sendteTilleggsopplysninger.captured.size)
        with(sendteTilleggsopplysninger.captured[0]) {
            assertEquals("periodeId", this.first)
            assertEquals(periodeId, this.second)
        }

        with(testRapid.inspektør) {
            assertEquals(1, size)
            assertEquals(journalpostId, field(0, "@løsning")["journalpostId"].asText())
        }
    }
}
