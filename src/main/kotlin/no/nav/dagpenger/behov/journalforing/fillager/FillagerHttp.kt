package no.nav.dagpenger.behov.journalforing.fillager

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import org.slf4j.MDC

class FillagerHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: () -> String,
) : Fillager {
    private val client = HttpClient(engine) { expectSuccess = true }

    override suspend fun hentFil(
        urn: FilURN,
        eier: String,
    ): ByteArray =
        client
            .get("http://dp-mellomlagring/v1/azuread/mellomlagring/vedlegg/${urn.fil}") {
                header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
                header("X-Eier", eier)
                header(HttpHeaders.XCorrelationId, MDC.get("behovId"))
            }.body()
}
