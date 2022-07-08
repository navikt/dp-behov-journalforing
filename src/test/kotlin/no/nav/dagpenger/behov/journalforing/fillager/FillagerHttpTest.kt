package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FillagerHttpTest {
    private val testFnr = "1654321876"

    @Test
    fun `svarer med innholdet i fila`() {
        runBlocking {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("flott fil"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val apiClient = FillagerHttp(mockEngine) { "token" }

            assertEquals(9, apiClient.hentFil(FilURN("urn:vedlegg:id/fil"), testFnr).size)
            assertEquals("Bearer token", mockEngine.requestHistory.first().headers[HttpHeaders.Authorization])
            assertEquals(testFnr, mockEngine.requestHistory.first().headers["X-Eier"])
        }
    }
}
