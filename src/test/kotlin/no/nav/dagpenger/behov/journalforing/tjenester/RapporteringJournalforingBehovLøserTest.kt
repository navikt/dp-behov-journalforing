package no.nav.dagpenger.behov.journalforing.tjenester

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Journalpost
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RapporteringJournalføringBehovLøserTest {

    private val journalpostApi = mockk<JournalpostApi>()
    private val testRapid = TestRapid().also {
        RapporteringJournalføringBehovLøser(it, journalpostApi)
    }

    @Test
    fun `løser behov for å opprette ny journalpost for rapportering`() {
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        coEvery {
            journalpostApi.opprett(any(), capture(sendteDokumenter), any())
        } returns Journalpost("journalpost123")

        testRapid.sendTestMessage(journalføreRapportering)

        assertEquals(1, sendteDokumenter.captured.size)
        with(sendteDokumenter.captured[0]) {
            assertEquals(this.brevkode, "M6")
            assertEquals(this.tittel, "Timelister")
            assertEquals(this.varianter.size, 1)
        }

        with(testRapid.inspektør) {
            assertEquals(1, size)
            assertEquals("journalpost123", field(0, "@løsning")["journalpostId"].asText())
            assertEquals("{}", field(0, "@løsning")["json"].asText())
        }
    }
}

@Language("JSON")
val journalføreRapportering = """{
  "@event_name": "behov",
  "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
  "@behov": [
    "JournalføreRapportering"
  ],
  "ident": "12345678913",
  "periodeId": "periodeId123",
  "JournalføreRapportering": {
    "json": "{}"
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
