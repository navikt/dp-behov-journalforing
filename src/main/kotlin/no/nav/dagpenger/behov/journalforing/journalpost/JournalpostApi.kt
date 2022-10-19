package no.nav.dagpenger.behov.journalforing.journalpost

internal interface JournalpostApi {
    suspend fun opprett(ident: String, dokumenter: List<Dokument>): Journalpost

    data class Journalpost(
        val id: String
    )

    data class Dokument(
        val brevkode: String?,
        val varianter: List<Variant>,
        val tittel: String? = null
    )

    data class Variant(
        val filtype: Filtype,
        val format: Format,
        val fysiskDokument: ByteArray
    ) {
        override fun toString() = "Variant(filtype=$filtype, format=$format)"

        enum class Filtype {
            PDF, PDFA, JPEG, TIFF, JSON, PNG,
        }

        enum class Format {
            ARKIV, ORIGINAL, FULLVERSJON,
        }
    }
}
