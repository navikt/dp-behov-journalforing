package no.nav.dagpenger.behov.journalforing.tjenester

import io.ktor.utils.io.core.toByteArray
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Journalpost
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype.JSON
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format
import no.nav.dagpenger.behov.journalforing.soknad.SoknadHttp
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JournalforingBehovLøserTest {
    private val fillager = mockk<Fillager>()
    private val journalpostApi = mockk<JournalpostApi>()
    private val faktahenter = mockk<SoknadHttp>()
    private val testRapid = TestRapid().also {
        JournalforingBehovLøser(it, fillager, journalpostApi, faktahenter)
    }

    @Test
    fun `løser behov for å opprette ny journalpost`() {
        coEvery {
            fillager.hentFil(any(), any())
        } returns "asdlfkjskljflk".toByteArray()
        coEvery {
            journalpostApi.opprett(any(), any())
        } returns Journalpost("journalpost123")
        coEvery {
            faktahenter.hentJsonSøknad(any())
        } returns JournalpostApi.Variant(JSON, Format.ORIGINAL, "{}".toByteArray())
        testRapid.sendTestMessage(testMessage)

        with(testRapid.inspektør) {
            assertEquals(1, size)
            assertEquals("journalpost123", field(0, "@løsning")["NyJournalpost"].asText())
        }
    }
}

@Language("JSON")
val testMessage = """{
  "@behov": [
    "NyJournalpost"
  ],
  "søknad_uuid": "hasfakfhajkfhkasjfhk",
  "ident": "12345678910",
  "dokumenter": [
    {
      "brevkode": "NAV 04-01.04",
      "varianter": [
        {
          "urn": "urn:vedlegg:soknadId/fil1",
          "format": "ARKIV",
          "type": "PDF"
        },
        {
          "urn": "urn:vedlegg:soknadId/fil2",
          "format": "FULLVERSJON",
          "type": "PDF"
        }
      ]
    }
  ]
}
""".trimIndent()
