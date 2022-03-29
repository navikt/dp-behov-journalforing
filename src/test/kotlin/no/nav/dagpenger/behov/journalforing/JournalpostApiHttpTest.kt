package no.nav.dagpenger.behov.journalforing

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class JournalpostApiHttpTest {
    @Test
    fun `oppretter journalposter`() {
        runBlocking {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel("127.0.0.1"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val apiClient = JournalpostApiHttp(mockEngine)
            /*assertEquals(
                "127.0.0.1",
                apiClient.opprett(
                    "urn",
                )
            )*/
        }
    }
}
