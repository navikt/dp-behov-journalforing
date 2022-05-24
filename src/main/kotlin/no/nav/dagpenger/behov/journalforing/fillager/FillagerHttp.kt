package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

class FillagerHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: () -> String
) :
    Fillager {
    private val client = HttpClient(engine) {}

    override suspend fun hentFil(urn: FilURN): ByteArray {
        return client.get("http://dp-mellomlagring/v1/azuread/mellomlagring/vedlegg/${urn.fil}") {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
        }.body()
    }
}
