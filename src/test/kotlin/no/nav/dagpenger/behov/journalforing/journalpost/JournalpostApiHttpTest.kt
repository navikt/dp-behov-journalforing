package no.nav.dagpenger.behov.journalforing.journalpost

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JournalpostApiHttpTest {
    @Test
    fun `oppretter journalposter`() {
        runBlocking {
            val mockEngine = MockEngine { request ->
                println(request.body)
                respond(
                    content = ByteReadChannel(dummyResponse),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val apiClient = JournalpostApiHttp(mockEngine)
            assertEquals(
                "467010363", apiClient.opprett("urn", emptyList())
            )
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
