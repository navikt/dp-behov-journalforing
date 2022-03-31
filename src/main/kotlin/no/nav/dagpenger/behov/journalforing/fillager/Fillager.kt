package no.nav.dagpenger.behov.journalforing.fillager

interface Fillager {
    suspend fun hentFil(urn: String): ByteArray
}
