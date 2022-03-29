package no.nav.dagpenger.behov.journalforing

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.json.JsonFeature
import no.nav.dagpenger.behov.journalforing.JournalpostApi.Dokument

internal class JournalpostApiHttp(engine: HttpClientEngine) : JournalpostApi {
    private val client = HttpClient(engine) {
        install(JsonFeature)
    }

    override fun opprett(ident: String, dokumenter: List<Dokument>): String {
        TODO("Not yet implemented")
    }
}
