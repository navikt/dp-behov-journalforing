package no.nav.dagpenger.behov.journalforing

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JournalforingBehovLoserTest {
    val testRapid = TestRapid().also {
        JournalforingBehovLoser(it)
    }

    @Test
    fun `besvarer pdf behov`() {
        testRapid.sendTestMessage(testMessage)
        assertEquals(1, testRapid.inspektør.size)
    }

    @Test
    fun `besvar ikke behov hvis løsning er besvart`() {
        testRapid.sendTestMessage(testMessageMedLøsning)
        assertEquals(0, testRapid.inspektør.size)
    }
}

@Language("JSON")
val testMessage = """ {
        "@behov": ["arkiverbarSøknad"],
        "søknad_uuid": "hasfakfhajkfhkasjfhk",
        "ident": "12345678910"
            }
""".trimIndent()

@Language("JSON")
val testMessageMedLøsning = """ {
        "@behov": ["arkiverbarSøknad"],
        "@løsning": "something",
        "søknad_uuid": "hasfakfhajkfhkasjfhk",
        "ident": "12345678910"
            }
""".trimIndent()