package no.nav.dagpenger.behov.journalforing.journalpost

internal interface JournalpostApi {
    suspend fun opprett(ident: String, dokumenter: List<Dokument>): String

    data class Dokument(
        val brevkode: String,
        val varianter: List<Dokumentvariant>
    )

    data class Dokumentvariant(
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
}
