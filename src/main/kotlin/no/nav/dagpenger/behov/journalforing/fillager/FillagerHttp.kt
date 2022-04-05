package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.dagpenger.aad.api.ClientCredentialsClient

class FillagerHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: ClientCredentialsClient
) :
    Fillager {
    private val client = HttpClient(engine) {}

    override suspend fun hentFil(urn: FilURN): ByteArray {
        return client.get("http://dp-mellomlagring/v1/mellomlagring/vedlegg/${urn.fil}") {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.getAccessToken()}")
        }
    }
}
