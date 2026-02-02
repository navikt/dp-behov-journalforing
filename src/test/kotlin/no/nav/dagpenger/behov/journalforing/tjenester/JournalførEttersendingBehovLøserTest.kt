package no.nav.dagpenger.behov.journalforing.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.utils.io.core.toByteArray
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Resultat
import no.nav.dagpenger.behov.journalforing.tjenester.JournalførEttersendingBehovLøser.Companion.BEHOV
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class JournalførEttersendingBehovLøserTest {
    private val fillager =
        mockk<Fillager>().also {
            coEvery {
                it.hentFil(any(), any())
            } returns "Hei, jeg er en fil!".toByteArray()
        }
    private val journalpostApi = mockk<JournalpostApi>()
    private val testRapid =
        TestRapid().also { JournalførEttersendingBehovLøser(it, fillager, journalpostApi) }

    private val seksjonData = "dokumentasjon" as String
    private val seksjonId = "verneplikt" as String

    @Test
    fun `journalfører ettersending av dokumentasjon`() {
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        coEvery {
            journalpostApi.opprett(any(), capture(sendteDokumenter), any(), any(), any())
        } returns Resultat("730212311", false, emptyList(), "Journalpost ferdigstilt")

        testRapid.sendTestMessage(melding)

        assert(sendteDokumenter.captured.size == 2)

        with(sendteDokumenter.captured[0]) {
            this.brevkode shouldBe "DOK1"
            this.tittel shouldBe "Ukjent dokumentittel"
            this.varianter.size shouldBe 2
        }
        with(sendteDokumenter.captured[1]) {
            this.brevkode shouldBe "DOK2"
            this.tittel shouldBe "Ukjent dokumentittel"
            this.varianter.size shouldBe 1
        }
        with(testRapid.inspektør) {
            size shouldBe 1
            field(0, "@løsning")[BEHOV]["journalpostId"].asText() shouldBe "730212311"
            field(0, "@løsning")[BEHOV]["journalførtTidspunkt"].asText() shouldNotBe null
            field(0, "@løsning")[BEHOV]["dokumentasjonskravJson"].asText() shouldBe seksjonData
            field(0, "@løsning")[BEHOV]["seksjonId"].asText() shouldBe seksjonId
        }
    }

    @Test
    fun `JournalførEttersendingBehovLøser leser ikke melding dersom hvis den inneholder @løsning`() {
        testRapid.sendTestMessage(meldingMedLøsning)

        testRapid.inspektør.size shouldBe 0
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `JournalførEttersendingBehovLøser publiserer feilmelding og kaster exception hvis kall til journalpostApi feiler med HTTP 500`() {
        val httpResponse = mockk<HttpResponse>(relaxed = true)
        every { httpResponse.status } returns InternalServerError
        coEvery { journalpostApi.opprett(any(), any(), any(), any(), eq(true)) } throws
            ClientRequestException(httpResponse, "Feil, feil, og atter feil!")

        val exception = shouldThrow<ClientRequestException> { testRapid.sendTestMessage(melding) }

        exception shouldNotBe null
        exception.message shouldContain "Feil, feil, og atter feil!"
        with(testRapid.inspektør) {
            size shouldBe 1
            message(0)
                .get("@event_name")
                .asText() shouldBe "journalfør_ettersending_av_dokumentasjon_feilet"
        }
    }

    @Language("JSON")
    val melding =
        """
        {
          "@event_name": "behov",
          "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
          "@behov": [
            "journalfør_ettersending_av_dokumentasjon"
          ],
          "søknadId": "19185bc3-7752-48c3-9886-c429c76b5041",
          "ident": "12345678913",
          "type": "NY_DIALOG",
          "innsendingId": "d0664505-e546-4cef-9e3f-8f49b85afb58",
          "journalfør_ettersending_av_dokumentasjon": {
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
            ],
            "dokumentasjonskravJson": "$seksjonData",
            "seksjonId": "$seksjonId"
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
    val meldingMedLøsning =
        """
        {
          "@event_name": "behov",
          "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
          "@behov": [
            "journalfør_ettersending_av_dokumentasjon"
          ],
          "søknadId": "19185bc3-7752-48c3-9886-c429c76b5041",
          "ident": "12345678913",
          "type": "NY_DIALOG",
          "innsendingId": "d0664505-e546-4cef-9e3f-8f49b85afb58",
          "@løsning": "løsningsforslag",
          "journalfør_ettersending_av_dokumentasjon": {
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
            ],
            "dokumentasjonskravJson": "$seksjonData",
            "seksjonId": "$seksjonId"
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
}
