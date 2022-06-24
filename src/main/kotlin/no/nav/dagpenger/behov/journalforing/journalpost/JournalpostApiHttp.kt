package no.nav.dagpenger.behov.journalforing.journalpost

import com.fasterxml.jackson.databind.DeserializationFeature
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.jackson.jackson
import no.nav.dagpenger.behov.journalforing.Configuration
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Journalpost
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Dokumentvariant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Dokumentvariant.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Journalpost.Bruker
import java.util.Base64

internal class JournalpostApiHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: () -> String,
    private val basePath: String = "rest/journalpostapi/v1",
) : JournalpostApi {
    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(Logging) {
            level = LogLevel.ALL
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
        client.post {
            url { encodedPath = "$basePath/journalpost" }
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
            contentType(ContentType.Application.Json)
            setBody(
                Journalpost(
                    avsenderMottaker = Bruker(ident),
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
            )
        }.body<Resultat>().let {
            Journalpost(it.journalpostId)
        }

    private data class Journalpost(
        val avsenderMottaker: Bruker,
        val bruker: Bruker,
        val dokumenter: List<Dokument>,
        private val journalposttype: String = "INNGAAENDE",
        private val tema: String = "DAG",
        private val kanal: String = "NAV_NO",
    ) {
        data class Bruker(
            val id: String,
            private val idType: String = "FNR",
        )
    }

    private data class Dokument(
        val brevkode: String,
        val dokumentvarianter: List<Dokumentvariant>,
    )

    private data class Dokumentvariant(
        val filtype: Filtype,
        val variantformat: Variant,
        val fysiskDokument: String,
    ) {
        enum class Filtype {
            PDF, PDFA, JPEG, TIFF, JSON, PNG,
        }

        enum class Variant {
            ARKIV, ORIGINAL, FULLVERSJON,
        }
    }

    private data class Resultat(
        val journalpostId: String,
        val dokumenter: List<DokumentInfo>,
    ) {
        data class DokumentInfo(val dokumentInfoId: String)
    }
}
