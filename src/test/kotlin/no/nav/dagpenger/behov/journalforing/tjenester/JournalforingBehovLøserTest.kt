package no.nav.dagpenger.behov.journalforing.tjenester

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.behov.journalforing.fillager.Fillager
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JournalforingBehovLøserTest {
    private val fillager = mockk<Fillager>()
    private val journalpostApi = mockk<JournalpostApi>()
    private val testRapid = TestRapid().also {
        JournalforingBehovLøser(it, fillager, journalpostApi)
    }

    @Test
    fun `løser behov for å opprette ny journalpost`() {
        coEvery {
            fillager.hentFil(any())
        } returns "asdlfkjskljflk"
        coEvery {
            journalpostApi.opprett(any(), any())
        } returns "journalpost123"

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
   "filer": [
     {
       "urn": "urn:dp-mellomlagring:123"
     },
     {
       "urn": "urn:dp-mellomlagring:345"
     }
   ]
}
""".trimIndent()
