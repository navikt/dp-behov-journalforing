package no.nav.dagpenger.behov.journalforing.journalpost

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokument
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype.JPEG
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype.PDF
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format.ARKIV
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format.FULLVERSJON
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JournalpostApiHttpTest {
    @Test
    fun `oppretter journalposter`() {
        runBlocking {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel(dummyResponse),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val apiClient = JournalpostApiHttp(mockEngine, mockk(relaxed = true))
            val journalpost = apiClient.opprett(
                "brukerident",
                listOf(
                    Dokument(
                        "123",
                        listOf(
                            Variant(JPEG, ARKIV, fysiskDokument = ByteArray(2)),
                            Variant(PDF, FULLVERSJON, fysiskDokument = ByteArray(2))
                        )
                    )
                )
            )
            assertEquals("467010363", journalpost.id)
        }
    }
}

@Language("JSON")
private val dummyResponse = """{
  "dokumenter": [
    {
      "dokumentInfoId": "123"
    }
  ],
  "journalpostId": "467010363",
  "journalpostferdigstilt": true
}
""".trimIndent()
