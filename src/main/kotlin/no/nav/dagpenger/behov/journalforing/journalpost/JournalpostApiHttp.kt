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
import io.ktor.client.plugins.HttpRequestRetry
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
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.JournalpostPayload.Bruker
import java.util.Base64

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class JournalpostApiHttp(
    engine: HttpClientEngine = CIO.create(),
    private val tokenProvider: () -> String,
    private val basePath: String = "rest/journalpostapi/v1",
) : JournalpostApi {
    private val client =
        HttpClient(engine) {
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
            install(HttpRequestRetry) {
                modifyRequest { request ->
                    request.headers.append("x-retry-count", retryCount.toString())
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
                requestTimeoutMillis = 300000
            }
            defaultRequest {
                header("X-Nav-Consumer", "dp-behov-journalforing")
                url {
                    protocol = URLProtocol.HTTPS
                    host = Configuration.properties[Key("DOKARKIV_INGRESS", stringType)]
                }
            }
        }

    override suspend fun opprett(
        forsøkFerdigstill: Boolean,
        payload: JournalpostPayload,
    ): Resultat {
        return client.post {
            url { encodedPath = "$basePath/journalpost?forsoekFerdigstill=$forsøkFerdigstill" }
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
            header(HttpHeaders.XCorrelationId, payload.eksternReferanseId)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body<Resultat>().also {
            logg.info { "Opprettet journalpost med id ${it.journalpostId} for behovId ${payload.eksternReferanseId}" }
        }
    }

    override suspend fun opprett(
        ident: String,
        dokumenter: List<JournalpostApi.Dokument>,
        eksternReferanseId: String,
        tilleggsopplysninger: List<Pair<String, String>>,
    ): Journalpost =
        client.post {
            url { encodedPath = "$basePath/journalpost" }
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
            header(HttpHeaders.XCorrelationId, eksternReferanseId)
            contentType(ContentType.Application.Json)
            setBody(
                JournalpostPayload(
                    avsenderMottaker = Bruker(ident),
                    bruker = Bruker(ident),
                    dokumenter =
                        dokumenter.map { dokument ->
                            Dokument(
                                brevkode = dokument.brevkode,
                                dokumentvarianter =
                                    dokument.varianter.map { variant ->
                                        Dokumentvariant(
                                            Filtype.valueOf(variant.filtype.toString()),
                                            Variant.valueOf(variant.format.toString()),
                                            Base64.getEncoder().encodeToString(variant.fysiskDokument),
                                        )
                                    },
                                tittel = dokument.tittel,
                            )
                        },
                    eksternReferanseId = eksternReferanseId,
                    tilleggsopplysninger = tilleggsopplysninger.map { Tilleggsopplysning(it.first, it.second) },
                ),
            )
        }.body<Resultat>().let {
            Journalpost(it.journalpostId)
        }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    internal data class JournalpostPayload(
        val avsenderMottaker: Bruker,
        val bruker: Bruker,
        val dokumenter: List<Dokument>,
        val eksternReferanseId: String,
        val tilleggsopplysninger: List<Tilleggsopplysning> = emptyList(),
        private val journalposttype: String = "INNGAAENDE",
        private val tema: String = "DAG",
        private val kanal: String = "NAV_NO",
        val tittel: String? = null,
        val journalfoerendeEnhet: String = "9999",
        val sak: Sak? = null,
    ) {
        @JsonAutoDetect(fieldVisibility = Visibility.ANY)
        data class Bruker(
            val id: String,
            private val idType: String = "FNR",
        )
    }

    internal data class Sak(
        val sakstype: String = "FAGSAK",
        val fagsakId: String,
        val fagsakSystem: String,
    )

    internal data class Dokument(
        val brevkode: String? = null,
        val dokumentvarianter: List<Dokumentvariant>,
        val tittel: String? = null,
    )

    internal data class Tilleggsopplysning(
        val nokkel: String,
        val verdi: String,
    )

    internal data class Dokumentvariant(
        val filtype: Filtype,
        val variantformat: Variant,
        val fysiskDokument: String,
    ) {
        enum class Filtype {
            PDF,
            PDFA,
            JPEG,
            TIFF,
            JSON,
            PNG,
        }

        enum class Variant {
            ARKIV,
            ORIGINAL,
            FULLVERSJON,
        }
    }

    internal data class Resultat(
        val journalpostId: String,
        val journalpostferdigstilt: Boolean,
        val dokumenter: List<DokumentInfo>,
        val melding: String,
    ) {
        data class DokumentInfo(val dokumentInfoId: String)
    }
}
