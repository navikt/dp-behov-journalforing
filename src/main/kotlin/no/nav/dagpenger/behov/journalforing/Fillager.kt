package no.nav.dagpenger.behov.journalforing

interface Fillager {
    suspend fun hentFil(urn: String): String
}
