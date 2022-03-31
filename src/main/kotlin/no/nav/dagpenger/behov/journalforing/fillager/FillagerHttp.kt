package no.nav.dagpenger.behov.journalforing.fillager

import de.slub.urn.URN
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.dagpenger.aad.api.ClientCredentialsClient

class FillagerHttp(engine: HttpClientEngine, private val tokenProvider: ClientCredentialsClient) : Fillager {
    private val client = HttpClient(engine) {}

    override suspend fun hentFil(urn: String): ByteArray {
        val filId = URN.rfc8141().parse(urn).namespaceSpecificString()
        return client.get("http://dp-mellomlagring/v1/mellomlagring/vedlegg/$filId") {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.getAccessToken()}")
        }
    }
}
