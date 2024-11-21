package no.nav.dagpenger.behov.journalforing.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.utils.io.core.toByteArray
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype.JSON
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Resultat
import no.nav.dagpenger.behov.journalforing.soknad.SoknadHttp
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JournalforingBehovLøserTest {
    private val fillager =
        mockk<Fillager>().also {
            coEvery {
                it.hentFil(any(), any())
            } returns "asdlfkjskljflk".toByteArray()
        }
    private val journalpostApi = mockk<JournalpostApi>()
    private val faktahenter =
        mockk<SoknadHttp>().also {
            coEvery {
                it.hentJsonSøknad(any())
            } returns JournalpostApi.Variant(JSON, Format.ORIGINAL, "{}".toByteArray())
        }
    private val testRapid =
        TestRapid().also {
            JournalforingBehovLøser(it, fillager, journalpostApi, faktahenter)
        }

    @Test
    fun `løser behov for å opprette ny journalpost for dagpenger`() {
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        coEvery {
            journalpostApi.opprett(any(), capture(sendteDokumenter), any(), any(), eq(true))
        } returns Resultat("journalpost123", false, emptyList(), "Journalpost ferdigstilt")

        testRapid.sendTestMessage(dagpengerInnsending)

        assertEquals(3, sendteDokumenter.captured.size)
        with(sendteDokumenter.captured[0]) {
            assertEquals(this.brevkode, "NAV 04-01.03")
            assertEquals(this.tittel, "Søknad om dagpenger (ikke permittert)")
            assertEquals(this.varianter.size, 3)
        }

        with(sendteDokumenter.captured[1]) {
            assertEquals(this.brevkode, "DOK1")
            assertEquals(this.tittel, "Ukjent dokumentittel")
            assertEquals(this.varianter.size, 2)
        }

        with(sendteDokumenter.captured[2]) {
            assertEquals(this.brevkode, "DOK2")
            assertEquals(this.tittel, "Ukjent dokumentittel")
            assertEquals(this.varianter.size, 1)
        }

        with(testRapid.inspektør) {
            assertEquals(1, size)
            assertEquals("journalpost123", field(0, "@løsning")["NyJournalpost"].asText())
        }
    }

    @Test
    fun `løser behov for å opprette ny journalpost for generell innsending`() {
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        coEvery {
            journalpostApi.opprett(any(), capture(sendteDokumenter), any(), any(), eq(true))
        } returns Resultat("journalpost123", false, emptyList(), "Journalpost ferdigstilt")

        testRapid.sendTestMessage(generellInnsending)

        assertEquals(2, sendteDokumenter.captured.size)
        with(sendteDokumenter.captured[0]) {
            assertEquals(this.brevkode, "GENERELL_INNSENDING")
            assertEquals(this.tittel, "Generell innsending")
            assertEquals(this.varianter.size, 3)
        }

        with(sendteDokumenter.captured[1]) {
            assertEquals(this.brevkode, "N6")
            assertEquals(this.tittel, "Annet")
            assertEquals(this.varianter.size, 2)
        }
    }
}

@Language("JSON")
val dagpengerInnsending =
    """
    {
      "@event_name": "behov",
      "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
      "@behov": [
        "NyJournalpost"
      ],
      "søknad_uuid": "19185bc3-7752-48c3-9886-c429c76b5041",
      "ident": "12345678913",
      "type": "NY_DIALOG",
      "innsendingId": "d0664505-e546-4cef-9e3f-8f49b85afb58",
      "NyJournalpost": {
        "hovedDokument": {
          "skjemakode": "04-01.03",
          "varianter": [
            {
              "filnavn": "netto.pdf",
              "urn": "urn:vedlegg:soknadId/netto.pdf",
              "variant": "ARKIV",
              "type": "PDF"
            },
            {
              "filnavn": "brutto.pdf",
              "urn": "urn:vedlegg:soknadId/brutto.pdf",
              "variant": "FULLVERSJON",
              "type": "PDF"
            }
          ]
        },
        "dokumenter": [
          {
            "skjemakode": "DOK1",
            "varianter": [
              {
                "filnavn": "DOK1A",
                "urn": "urn:vedlegg:soknadId/dok1a.pdf",
                "variant": "ARKIV",
                "type": "PDF"
              },
              {
                "filnavn": "DOK1B",
                "urn": "urn:vedlegg:soknadId/dok1b.pdf",
                "variant": "FULLVERSJON",
                "type": "PDF"
              }
            ]
          },
          {
            "skjemakode": "DOK2",
            "varianter": [
              {
                "filnavn": "dok2.pdf",
                "urn": "urn:vedlegg:soknadId/dok2.pdf",
                "variant": "ARKIV",
                "type": "PDF"
              }
            ]
          }
        ]
      },
      "hovedDokument": {},
      "dokumenter": [],
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

@Language("JSON")
private val generellInnsending: String =
    """
    {
      "@event_name": "behov",
      "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
      "@behov": [
        "NyJournalpost"
      ],
      "søknad_uuid": "19185bc3-7752-48c3-9886-c429c76b5041",
      "ident": "12345678913",
      "type": "NY_DIALOG",
      "innsendingId": "d0664505-e546-4cef-9e3f-8f49b85afb58",
      "NyJournalpost": {
        "hovedDokument": {
          "skjemakode": "GENERELL_INNSENDING",
          "varianter": [
            {
              "filnavn": "netto.pdf",
              "urn": "urn:vedlegg:soknadId/netto.pdf",
              "variant": "ARKIV",
              "type": "PDF"
            },
            {
              "filnavn": "brutto.pdf",
              "urn": "urn:vedlegg:soknadId/brutto.pdf",
              "variant": "FULLVERSJON",
              "type": "PDF"
            }
          ]
        },
        "dokumenter": [
          {
            "skjemakode": "N6",
            "varianter": [
              {
                "filnavn": "DOK1A",
                "urn": "urn:vedlegg:soknadId/dok1a.pdf",
                "variant": "ARKIV",
                "type": "PDF"
              },
              {
                "filnavn": "DOK1B",
                "urn": "urn:vedlegg:soknadId/dok1b.pdf",
                "variant": "FULLVERSJON",
                "type": "PDF"
              }
            ]
          }
        ]
      },
      "hovedDokument": {},
      "dokumenter": [],
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
