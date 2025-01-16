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

internal class MinidialogJournalføringBehovLøserTest {
    private val behov = "JournalføreMinidialog"
    private val søknadId = "19185bc3-7752-48c3-9886-c429c76b5052"
    private val behovId = "34f6743c-bd9a-4902-ae68-fae0171b1e79"
    private val dialogId = "34f6743c-bd9a-4902-ae68-fae0171b1e90"
    private val ident = "01020312345"
    private val base64EncodedPdf = "UERG"

    private val journalpostId = "journalpost123"
    private val json = "{\"key1\": \"value1\"}"

    private val minidialogJournalføringBehov =
        """
        {
          "@event_name": "behov",
          "@behovId": "$behovId",
          "@behov": [
            "$behov"
          ],
          "søknad_uuid": "$søknadId",
          "ident": "$ident",
          "JournalføreMinidialog": {
            "skjemakode": "04-01.03",
            "dialog_uuid": "$dialogId",
            "tittel": "Arbeidsforhold",
            "json": "{\"key1\": \"value1\"}",
            "pdf": "$base64EncodedPdf"
          },
          "@id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
          "@opprettet": "2023-10-23T18:53:08.056035121",
          "system_read_count": 0,
          "system_participating_services": [
            {
                "id": "c1116672-9057-406b-93e1-7198f7282126",
                "time": "2022-09-26T09:47:25.411111"
            }
          ]
        }
        """.trimIndent().replace("\n", "")

    private val journalpostApi = mockk<JournalpostApi>()
    private val testRapid =
        TestRapid().also {
            MinidialogJournalføringBehovLøser(it, journalpostApi)
        }

    @Test
    fun `løser behov for å opprette ny journalpost for minidialog`() {
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        coEvery {
            journalpostApi.opprett(
                ident = eq(ident),
                dokumenter = capture(sendteDokumenter),
                eksternReferanseId = eq(behovId),
                forsøkFerdigstill = eq(true),
                tittel = "Arbeidsforhold",
            )
        } returns Resultat(journalpostId, true, emptyList(), "Journalpost ferdigstilt")

        testRapid.sendTestMessage(minidialogJournalføringBehov)

        assertEquals(1, sendteDokumenter.captured.size)
        with(sendteDokumenter.captured[0]) {
            assertEquals("04-01.03", this.brevkode)
            assertEquals("Arbeidsforhold", this.tittel)
            assertEquals(2, this.varianter.size)
            assertEquals(JournalpostApi.Variant.Filtype.JSON, this.varianter[0].filtype)
            assertEquals(JournalpostApi.Variant.Format.ORIGINAL, this.varianter[0].format)
            assertContentEquals(json.toByteArray(), this.varianter[0].fysiskDokument)
            assertEquals(JournalpostApi.Variant.Filtype.PDFA, this.varianter[1].filtype)
            assertEquals(JournalpostApi.Variant.Format.ARKIV, this.varianter[1].format)
            assertContentEquals(Base64.getDecoder().decode(base64EncodedPdf), this.varianter[1].fysiskDokument)
        }

        with(testRapid.inspektør) {
            assertEquals(1, size)
            assertEquals(journalpostId, field(0, "@løsning")["JournalføreMinidialog"].asText())
        }
    }
}
