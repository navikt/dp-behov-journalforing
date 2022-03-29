package no.nav.dagpenger.behov.journalforing

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json

class FillagerHttp(engine: HttpClientEngine) : Fillager {
    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json()
        }
    }

    override suspend fun hentFil(urn: String): String {
        return client.get("http://dp-mellomlagring/v1/mellomlagring/vedlegg/$urn").body()
    }
}
