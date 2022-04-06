package no.nav.dagpenger.behov.journalforing.journalpost

import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.dagpenger.aad.api.ClientCredentialsClient
import no.nav.dagpenger.behov.journalforing.Configuration
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Journalpost
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Dokumentvariant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Dokumentvariant.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Journalpost.Bruker
import java.util.Base64

internal class JournalpostApiHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: ClientCredentialsClient,
    private val basePath: String = "rest/journalpostapi/v1",
) : JournalpostApi {
    private val client = HttpClient(engine) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            )
        }
        defaultRequest {
            header("X-Nav-Consumer", "dp-behov-journalforing")
            url {
                protocol = URLProtocol.HTTPS
                host = Configuration.properties[Key("DOKARKIV_INGRESS", stringType)]
            }
        }
    }

    override suspend fun opprett(ident: String, dokumenter: List<JournalpostApi.Dokument>) =
        client.post<Resultat>() {
            url { encodedPath = "$basePath/journalpost" }
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.getAccessToken()}")
            contentType(ContentType.Application.Json)
            body = Journalpost(
                avsenderMottaker = Bruker(ident),
                behandlingstema = "ab0001",
                bruker = Bruker(ident),
                dokumenter = dokumenter.map { dokument ->
                    Dokument(
                        dokument.brevkode,
                        dokument.varianter.map { variant ->
                            Dokumentvariant(
                                Filtype.valueOf(variant.filtype.toString()),
                                Variant.valueOf(variant.format.toString()),
                                Base64.getEncoder().encodeToString(variant.fysiskDokument)
                            )
                        }
                    )
                }
            )
        }.let {
            Journalpost(it.journalpostId)
        }

    @Serializable
    private data class Journalpost(
        val avsenderMottaker: Bruker,
        val behandlingstema: String,
        val bruker: Bruker,
        val dokumenter: List<Dokument>,
        private val tema: String = "DAG",
        private val kanal: String = "NAV_NO",
    ) {
        @Serializable
        data class Bruker(
            val id: String,
            private val idType: String = "FNR",
        )
    }

    @Serializable
    private data class Dokument(
        val brevkode: String,
        val varianter: List<Dokumentvariant>,
    )

    @Serializable
    private data class Dokumentvariant(
        val filtype: Filtype,
        val variant: Variant,
        val fysiskDokument: String,
    ) {
        enum class Filtype {
            PDF, PDFA, JPEG, TIFF, JSON, PNG,
        }

        enum class Variant {
            ARKIV, ORIGINAL, FULLVERSJON,
        }
    }

    @Serializable
    private data class Resultat(
        val journalpostId: String,
        val dokumenter: List<DokumentInfo>,
    ) {
        @Serializable
        data class DokumentInfo(val dokumentInfoId: String)
    }
}
