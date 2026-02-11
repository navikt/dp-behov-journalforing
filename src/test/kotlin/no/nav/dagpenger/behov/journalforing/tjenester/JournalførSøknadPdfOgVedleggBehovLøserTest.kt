package no.nav.dagpenger.behov.journalforing.tjenester

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.utils.io.core.toByteArray
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype.JSON
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format.ORIGINAL
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Resultat
import no.nav.dagpenger.behov.journalforing.tjenester.JournalførSøknadPdfOgVedleggBehovLøser.Companion.BEHOV
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDateTime.now

internal class JournalførSøknadPdfOgVedleggBehovLøserTest {
    private val fillager =
        mockk<Fillager>().also {
            coEvery {
                it.hentFil(any(), any())
            } returns "Hei, jeg er en fil!".toByteArray()
        }
    private val journalpostApi = mockk<JournalpostApi>()
    private val testRapid =
        TestRapid().also {
            JournalførSøknadPdfOgVedleggBehovLøser(it, fillager, journalpostApi)
        }

    @Test
    fun `JournalførSøknadPdfOgVedleggBehovLøser løser behov som forventet for pdf-variant`() {
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        coEvery {
            journalpostApi.opprett(any(), capture(sendteDokumenter), any(), any(), any())
        } returns Resultat("730212311", false, emptyList(), "Journalpost ferdigstilt")

        testRapid.sendTestMessage(melding)

        sendteDokumenter.captured.size shouldBe 3
        with(sendteDokumenter.captured[0]) {
            this.brevkode shouldBe "NAV 04-01.03"
            this.tittel shouldBe "Søknad om dagpenger (ikke permittert)"
            this.varianter.size shouldBe 2
        }
        with(sendteDokumenter.captured[1]) {
            this.brevkode shouldBe "DOK1"
            this.tittel shouldBe "Ukjent dokumentittel"
            this.varianter.size shouldBe 2
        }
        with(sendteDokumenter.captured[2]) {
            this.brevkode shouldBe "DOK2"
            this.tittel shouldBe "Ukjent dokumentittel"
            this.varianter.size shouldBe 1
        }
        with(testRapid.inspektør) {
            size shouldBe 1
            field(0, "@løsning")[BEHOV]["journalpostId"].asText() shouldBe "730212311"
            field(0, "@løsning")[BEHOV]["journalførtTidspunkt"].asText() shouldNotBe null
        }
        coVerify {
            journalpostApi.opprett(
                "12345678913",
                any(),
                "34f6743c-bd9a-4902-ae68-fae0171b1e68",
                any(),
                true,
                any(),
            )
        }
    }

    @Test
    fun `JournalførSøknadPdfOgVedleggBehovLøser løser behov som forventet med json-variant`() {
        val forventetJsonString = "{\"versjon_navn\":\"Dagpenger\",\"søknad_uuid\":\"123e4567-e89b-12d3-a456-426614174000\"}"
        val sendteDokumenter = slot<List<JournalpostApi.Dokument>>()
        coEvery {
            journalpostApi.opprett(any(), capture(sendteDokumenter), any(), any(), any())
        } returns Resultat("730212311", false, emptyList(), "Journalpost ferdigstilt")

        testRapid.sendTestMessage(meldingMedJsonVariant)

        sendteDokumenter.captured.size shouldBe 1
        with(sendteDokumenter.captured[0]) {
            this.brevkode shouldBe "NAV 04-01.03"
            this.tittel shouldBe "Søknad om dagpenger (ikke permittert)"
            this.varianter.size shouldBe 3
            this.varianter[2].let { variant ->
                variant.filtype shouldBe JSON
                variant.format shouldBe ORIGINAL
                variant.fysiskDokument shouldBe jacksonObjectMapper().writeValueAsBytes(forventetJsonString)
            }
        }
    }

    @Test
    fun `JournalførSøknadPdfOgVedleggBehovLøser leser ikke melding dersom hvis den inneholder @løsning`() {
        testRapid.sendTestMessage(meldingMedLøsning)

        testRapid.inspektør.size shouldBe 0
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `JournalførSøknadPdfOgVedleggBehovLøser publiserer feilmelding og kaster exception hvis kall til journalpostApi feiler med HTTP 500`() {
        val httpResponse = mockk<HttpResponse>(relaxed = true)
        every { httpResponse.status } returns InternalServerError
        coEvery { journalpostApi.opprett(any(), any(), any(), any(), any()) } throws
            ClientRequestException(httpResponse, "Feil, feil, og atter feil!")

        val exception = shouldThrow<ClientRequestException> { testRapid.sendTestMessage(melding) }

        exception shouldNotBe null
        exception.message shouldContain "Feil, feil, og atter feil!"
        with(testRapid.inspektør) {
            size shouldBe 1
            message(0)
                .get("@event_name")
                .asText() shouldBe "journalfør_søknad_pdf_og_vedlegg_feilet"
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `JournalførSøknadPdfOgVedleggBehovLøser publiserer feilmelding og kaster exception hvis kall til journalpostApi feiler med HTTP 404`() {
        val httpResponse = mockk<HttpResponse>(relaxed = true)
        every { httpResponse.status } returns NotFound
        coEvery { journalpostApi.opprett(any(), any(), any(), any(), any()) } throws
            ClientRequestException(httpResponse, "Feil, feil, og atter feil!")

        val exception = shouldThrow<ClientRequestException> { testRapid.sendTestMessage(melding) }

        exception shouldNotBe null
        exception.message shouldContain "Feil, feil, og atter feil!"
        with(testRapid.inspektør) {
            size shouldBe 1
            message(0)
                .get("@event_name")
                .asText() shouldBe "journalfør_søknad_pdf_og_vedlegg_feilet"
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `JournalførSøknadPdfOgVedleggBehovLøser publiserer ikke feilmelding, men kaster exception hvis kall til journalpostApi feiler med noe annet enn HTTP 404 eller 500`() {
        val httpResponse = mockk<HttpResponse>(relaxed = true)
        every { httpResponse.status } returns BadRequest
        coEvery { journalpostApi.opprett(any(), any(), any(), any(), any()) } throws
            ClientRequestException(httpResponse, "Feil, feil, og atter feil!")

        val exception = shouldThrow<ClientRequestException> { testRapid.sendTestMessage(melding) }

        exception shouldNotBe null
        exception.message shouldContain "Feil, feil, og atter feil!"
        testRapid.inspektør.size shouldBe 0
    }

    @Language("JSON")
    val melding =
        """
        {
          "@event_name": "behov",
          "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
          "@behov": [
            "journalfør_søknad_pdf_og_vedlegg"
          ],
          "søknadId": "19185bc3-7752-48c3-9886-c429c76b5041",
          "ident": "12345678913",
          "type": "NY_DIALOG",
          "innsendingId": "d0664505-e546-4cef-9e3f-8f49b85afb58",
          "journalfør_søknad_pdf_og_vedlegg": {
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
    val meldingMedLøsning =
        """
        {
          "@event_name": "behov",
          "@behovId": "34f6743c-bd9a-4902-ae68-fae0171b1e68",
          "@behov": [
            "journalfør_søknad_pdf_og_vedlegg"
          ],
          "søknadId": "19185bc3-7752-48c3-9886-c429c76b5041",
          "ident": "12345678913",
          "type": "NY_DIALOG",
          "innsendingId": "d0664505-e546-4cef-9e3f-8f49b85afb58",
          "@løsning": "Dette er en slags løsning",
          "journalførtTidspunkt": "${now()}",
          "journalfør_søknad_pdf_og_vedlegg": {
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
    val meldingMedJsonVariant =
        """
        {
          "@event_name" : "behov",
          "@behovId" : "c1ba3588-e831-4129-9d41-3e83d777c1b9",
          "@behov" : [ "journalfør_søknad_pdf_og_vedlegg" ],
          "søknadId" : "123e4567-e89b-12d3-a456-426614174000",
          "ident" : "12345678903",
          "type" : "NY_DIALOG",
          "journalfør_søknad_pdf_og_vedlegg" : {
            "hovedDokument" : {
              "skjemakode" : "04-01.03",
              "varianter" : [ {
                "uuid" : "3e0e0132-29c8-4183-a12d-55c903588158",
                "filnavn" : "netto.pdf",
                "urn" : "urn:vedlegg:soknadId/netto.pdf",
                "json" : null,
                "variant" : "ARKIV",
                "type" : "PDF"
              }, {
                "uuid" : "69be2215-8ee9-4b6b-b349-ff8e1df422a5",
                "filnavn" : "brutto.pdf",
                "urn" : "urn:vedlegg:soknadId/brutto.pdf",
                "json" : null,
                "variant" : "FULLVERSJON",
                "type" : "PDF"
              }, {
                "uuid" : "f575bd00-e93f-4a75-b22c-9bd818c2cebc",
                "filnavn" : "json",
                "urn" : "urn:nav:dagpenger:json",
                "json" : "{\"versjon_navn\":\"Dagpenger\",\"søknad_uuid\":\"123e4567-e89b-12d3-a456-426614174000\"}",
                "variant" : "ORIGINAL",
                "type" : "JSON"
              } ]
            },
            "dokumenter" : [ ]
          },
          "@id" : "4df3f1a1-24ff-4c87-97b1-100cdc10a2df",
          "@opprettet" : "2026-02-11T18:14:41.897835485",
          "system_read_count" : 0,
          "system_participating_services" : [ {
            "id" : "4df3f1a1-24ff-4c87-97b1-100cdc10a2df",
            "time" : "2026-02-11T18:14:41.897835485"
          } ]
        }
        """.trimIndent()
}
