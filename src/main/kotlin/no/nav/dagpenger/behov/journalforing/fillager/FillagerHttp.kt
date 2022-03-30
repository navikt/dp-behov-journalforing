package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.dagpenger.aad.api.ClientCredentialsClient

class FillagerHttp(engine: HttpClientEngine, private val tokenProvider: ClientCredentialsClient) : Fillager {
    private val client = HttpClient(engine) {
        install(JsonFeature) {}
    }

    override suspend fun hentFil(urn: String): String {
        return client.get("http://dp-mellomlagring/v1/mellomlagring/vedlegg/$urn") {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.getAccessToken()}")
        }
    }
}
