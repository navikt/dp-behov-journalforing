package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get

class FillagerHttp(engine: HttpClientEngine) : Fillager {
    private val client = HttpClient(engine) {
        install(JsonFeature) {}
    }

    override suspend fun hentFil(urn: String): String {
        return client.get("http://dp-mellomlagring/v1/mellomlagring/vedlegg/$urn")
    }
}
