package no.nav.dagpenger.behov.journalforing.journalpost

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.dagpenger.behov.journalforing.Configuration
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Journalpost
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Dokumentvariant.Filtype
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Dokumentvariant.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Journalpost.Bruker
import java.util.Base64

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class JournalpostApiHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: () -> String,
    private val basePath: String = "rest/journalpostapi/v1",
) : JournalpostApi {
    private val client = HttpClient(engine) {
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status != HttpStatusCode.Conflict && response.status != HttpStatusCode.Created) {
                    throw ClientRequestException(response, response.bodyAsText())
                }
            }
            handleResponseExceptionWithRequest { exception, _ ->
                val responseException = exception as? ResponseException ?: return@handleResponseExceptionWithRequest
                sikkerlogg.error(responseException) { "Kall mot journalpostapi feilet." }
                throw responseException
            }
        }
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
            }
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
        defaultRequest {
            header("X-Nav-Consumer", "dp-behov-journalforing")
            url {
                protocol = URLProtocol.HTTPS
                host = Configuration.properties[Key("DOKARKIV_INGRESS", stringType)]
            }
        }
    }

    override suspend fun opprett(ident: String, dokumenter: List<JournalpostApi.Dokument>, eksternReferanseId: String) =
        client.post {
            url { encodedPath = "$basePath/journalpost" }
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
            header(HttpHeaders.XRequestId, eksternReferanseId)
            contentType(ContentType.Application.Json)
            setBody(
                Journalpost(
                    avsenderMottaker = Bruker(ident),
                    bruker = Bruker(ident),
                    eksternReferanseId = eksternReferanseId,
                    dokumenter = dokumenter.map { dokument ->
                        Dokument(
                            brevkode = dokument.brevkode,
                            dokumentvarianter = dokument.varianter.map { variant ->
                                Dokumentvariant(
                                    Filtype.valueOf(variant.filtype.toString()),
                                    Variant.valueOf(variant.format.toString()),
                                    Base64.getEncoder().encodeToString(variant.fysiskDokument),
                                )
                            },
                            tittel = dokument.tittel,
                        )
                    },
                ),
            )
        }.body<Resultat>().let {
            Journalpost(it.journalpostId)
        }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    private data class Journalpost(
        val avsenderMottaker: Bruker,
        val bruker: Bruker,
        val dokumenter: List<Dokument>,
        val eksternReferanseId: String,
        private val journalposttype: String = "INNGAAENDE",
        private val tema: String = "DAG",
        private val kanal: String = "NAV_NO",
    ) {
        @JsonAutoDetect(fieldVisibility = Visibility.ANY)
        data class Bruker(
            val id: String,
            private val idType: String = "FNR",
        )
    }

    private data class Dokument(
        val brevkode: String?,
        val dokumentvarianter: List<Dokumentvariant>,
        val tittel: String? = null,
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
