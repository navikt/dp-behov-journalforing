package no.nav.dagpenger.behov.journalforing.fillager

import de.slub.urn.URN
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
        val urn = URN.rfc8141().parse(urn)
        return client.get("http://dp-mellomlagring/v1/mellomlagring/vedlegg/${urn.namespaceSpecificString()}") {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.getAccessToken()}")
        }
    }
}
