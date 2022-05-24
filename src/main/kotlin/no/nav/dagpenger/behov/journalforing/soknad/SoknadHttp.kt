package no.nav.dagpenger.behov.journalforing.soknad

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant

internal class SoknadHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: () -> String
) {
    val baseurl = "http://dp-soknad/arbeid/dagpenger/soknadapi"
    private val client = HttpClient(engine) {}
    internal suspend fun hentJsonSøknad(søknadId: String): Variant {
        return client.get("$baseurl/$søknadId/ferdigstilt/fakta") {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
        }.body<ByteArray>().let {
            Variant(
                filtype = Variant.Filtype.JSON,
                format = Variant.Format.ORIGINAL,
                fysiskDokument = it
            )
        }
    }
}
