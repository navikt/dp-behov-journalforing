package no.nav.dagpenger.behov.journalforing.journalpost

internal interface JournalpostApi {
    suspend fun opprett(
        ident: String,
        dokumenter: List<Dokument>,
        eksternReferanseId: String,
        tilleggsopplysninger: List<Pair<String, String>> = emptyList(),
    ): Journalpost

    data class Journalpost(
        val id: String
    )

    data class Dokument(
        val brevkode: String?,
        val tittel: String? = null,
        val varianter: List<Variant>
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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Variant

            if (filtype != other.filtype) return false
            if (format != other.format) return false
            if (!fysiskDokument.contentEquals(other.fysiskDokument)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = filtype.hashCode()
            result = 31 * result + format.hashCode()
            result = 31 * result + fysiskDokument.contentHashCode()
            return result
        }
    }
}
