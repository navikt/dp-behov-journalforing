package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.aad.api.ClientCredentialsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FillagerHttpTest {
    private val tokenProvider = mockk<ClientCredentialsClient>()

    @Test
    fun sampleClientTest() {
        runBlocking {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel("127.0.0.1"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            coEvery {
                tokenProvider.getAccessToken()
            } returns "token"

            val apiClient = FillagerHttp(mockEngine, tokenProvider)

            assertEquals("127.0.0.1", apiClient.hentFil("urn"))
            assertEquals("Bearer token", mockEngine.requestHistory.first().headers[HttpHeaders.Authorization])
        }
    }
}
