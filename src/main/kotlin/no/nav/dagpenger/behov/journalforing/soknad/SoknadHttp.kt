package no.nav.dagpenger.behov.journalforing.soknad

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant
import org.slf4j.MDC

internal class SoknadHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: () -> String,
) {
    private val baseurl = "http://dp-soknad/arbeid/dagpenger/soknadapi"
    private val client = HttpClient(engine) {
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
        expectSuccess = true
    }

    internal suspend fun hentJsonSøknad(søknadId: String): Variant {
        return client.get("$baseurl/$søknadId/ferdigstilt/fakta") {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
            header(HttpHeaders.XCorrelationId, MDC.get("behovId"))
        }.body<ByteArray>().let {
            Variant(
                filtype = Variant.Filtype.JSON,
                format = Variant.Format.ORIGINAL,
                fysiskDokument = it,
            )
        }
    }
}
