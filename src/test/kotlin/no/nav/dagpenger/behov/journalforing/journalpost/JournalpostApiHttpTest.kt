package no.nav.dagpenger.behov.journalforing.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteReadPacket
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readText
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Dokument
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype.JPEG
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Filtype.PDF
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format.ARKIV
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApi.Variant.Format.FULLVERSJON
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

internal class JournalpostApiHttpTest {
    private companion object {
        val jacksonObjectMapper = jacksonObjectMapper()
    }

    @ParameterizedTest(name = "Oppretter journalpost gir status {index}")
    @ValueSource(ints = [201, 409])
    fun `oppretter journalposter uten tilleggsopplysninger`(status: Int) {
        test(status)
    }

    @Test
    fun `oppretter journalposter med tilleggsopplysninger`() {
        test(
            201,
            listOf(
                Pair("nøkkel1", "verdi1"),
                Pair("nøkkel2", ""),
            ),
        )
    }

    @Test
    fun `kaster exception ved ekte feil`() {
        runBlocking {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(exceptionResponse),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val apiClient = JournalpostApiHttp(mockEngine, mockk(relaxed = true))
            assertThrows<ClientRequestException> {
                apiClient.opprett("brukerident", listOf(), UUID.randomUUID().toString())
            }
        }
    }

    private fun test(
        status: Int,
        tilleggsopplysninger: List<Pair<String, String>> = emptyList(),
    ) {
        val eksternReferanseId = UUID.randomUUID().toString()
        runBlocking {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(dummyResponse),
                        status = HttpStatusCode.fromValue(status),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val apiClient = JournalpostApiHttp(mockEngine, mockk(relaxed = true))
            val resultat =
                apiClient.opprett(
                    "brukerident",
                    listOf(
                        Dokument(
                            brevkode = "123",
                            tittel = "dagpengersøknad",
                            varianter =
                                listOf(
                                    Variant(JPEG, ARKIV, fysiskDokument = ByteArray(2)),
                                    Variant(PDF, FULLVERSJON, fysiskDokument = ByteArray(2)),
                                ),
                        ),
                        Dokument(
                            brevkode = "456",
                            tittel = "vedleggtittel",
                            varianter =
                                listOf(
                                    Variant(JPEG, ARKIV, fysiskDokument = ByteArray(2)),
                                ),
                        ),
                    ),
                    eksternReferanseId,
                    tilleggsopplysninger,
                )

            with(mockEngine.requestHistory.first()) {
                val journalpost = jacksonObjectMapper.readTree(this.body.toByteReadPacket().readText())
                // dokarkiv kjører duplikatkontroll på eksternReferanseId
                assertEquals(eksternReferanseId, journalpost["eksternReferanseId"].asText())
                assertEquals("brukerident", journalpost["avsenderMottaker"]["id"].asText())
                assertEquals("FNR", journalpost["avsenderMottaker"]["idType"].asText())
                assertEquals("brukerident", journalpost["bruker"]["id"].asText())
                assertEquals("FNR", journalpost["bruker"]["idType"].asText())
                val førsteDokument = journalpost["dokumenter"].first()
                assertEquals("123", førsteDokument["brevkode"].asText())
                assertEquals("dagpengersøknad", førsteDokument["tittel"].asText())
                assertEquals("JPEG", førsteDokument["dokumentvarianter"][0]["filtype"].asText())
                assertEquals("ARKIV", førsteDokument["dokumentvarianter"][0]["variantformat"].asText())
                assertNotNull(førsteDokument["dokumentvarianter"][0]["fysiskDokument"])
                assertEquals("PDF", førsteDokument["dokumentvarianter"][1]["filtype"].asText())
                assertEquals("FULLVERSJON", førsteDokument["dokumentvarianter"][1]["variantformat"].asText())
                assertNotNull(førsteDokument["dokumentvarianter"][1]["fysiskDokument"])
                val andreDokument = journalpost["dokumenter"].last()
                assertEquals("456", andreDokument["brevkode"].asText())
                assertEquals("vedleggtittel", andreDokument["tittel"].asText())

                if (tilleggsopplysninger.isNotEmpty()) {
                    journalpost["tilleggsopplysninger"].asIterable().forEachIndexed { index, element ->
                        assertEquals(tilleggsopplysninger[index].first, element["nokkel"].asText())
                        if (tilleggsopplysninger[index].second.isBlank()) {
                            assertEquals(
                                "UKJENT",
                                element["verdi"].asText(),
                            )
                        } else {
                            assertEquals(tilleggsopplysninger[index].second, element["verdi"].asText())
                        }
                    }
                }
            }
            assertEquals("467010363", resultat.journalpostId)
        }
    }
}

@Language("JSON")
private val dummyResponse =
    """
    {
      "dokumenter": [
        {
          "dokumentInfoId": "123"
        }
      ],
      "journalpostId": "467010363",
      "journalpostferdigstilt": true,
      "melding": "En melding"
    }
    """.trimIndent()

@Language("JSON")
private val exceptionResponse =
    """
    {
        "error": "feil"
    }
    """.trimIndent()
