package no.nav.dagpenger.behov.journalforing.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteReadPacket
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
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

internal class JournalpostApiHttpTest {

    private companion object {
        val jacksonObjectMapper = jacksonObjectMapper()
    }

    @Test
    fun `oppretter journalposter`() {
        runBlocking {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel(dummyResponse),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val apiClient = JournalpostApiHttp(mockEngine, mockk(relaxed = true))
            val journalpost = apiClient.opprett(
                "brukerident",
                listOf(
                    Dokument(
                        brevkode = "123",
                        tittel = "dagpengersøknad",
                        varianter = listOf(
                            Variant(JPEG, ARKIV, fysiskDokument = ByteArray(2)),
                            Variant(PDF, FULLVERSJON, fysiskDokument = ByteArray(2))
                        )
                    ),
                    Dokument(
                        brevkode = "456",
                        tittel = "vedleggtittel",
                        varianter = listOf(
                            Variant(JPEG, ARKIV, fysiskDokument = ByteArray(2))
                        )
                    )
                )
            )

            with(mockEngine.requestHistory.first()) {
                val journalpost = jacksonObjectMapper.readTree(this.body.toByteReadPacket().readText())

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
            }
            assertEquals("467010363", journalpost.id)
        }
    }
}

@Language("JSON")
private val dummyResponse = """{
  "dokumenter": [
    {
      "dokumentInfoId": "123"
    }
  ],
  "journalpostId": "467010363",
  "journalpostferdigstilt": true
}
""".trimIndent()
