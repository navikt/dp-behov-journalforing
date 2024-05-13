package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FillagerHttpTest {
    private val testFnr = "1654321876"

    @Test
    fun `svarer med innholdet i fila`() {
        runBlocking {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("flott fil"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val apiClient = FillagerHttp(mockEngine) { "token" }

            assertEquals(9, apiClient.hentFil(FilURN("urn:vedlegg:id/fil"), testFnr).size)
            assertEquals("Bearer token", mockEngine.requestHistory.first().headers[HttpHeaders.Authorization])
            assertEquals(testFnr, mockEngine.requestHistory.first().headers["X-Eier"])
        }
    }

    @Test
    fun `test hentFil with 403 response`() {
        val mockEngine = MockEngine { respond("", HttpStatusCode.Forbidden) }
        val fillagerHttp = FillagerHttp(mockEngine) { "fake_token" }
        assertThrows<ClientRequestException> {
            runBlocking {
                fillagerHttp.hentFil(FilURN("urn:fake:foo"), "fake_eier")
            }
        }
    }
}
