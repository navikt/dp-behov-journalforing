package no.nav.dagpenger.behov.journalforing

interface JournalpostApi {
    fun opprett(ident: String, dokumenter: List<Map<String, String>>): String
}
