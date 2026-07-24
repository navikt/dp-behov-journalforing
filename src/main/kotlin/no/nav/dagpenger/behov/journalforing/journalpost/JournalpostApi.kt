package no.nav.dagpenger.behov.journalforing.journalpost

import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp.Sak
import kotlin.math.log10
import kotlin.math.pow

internal interface JournalpostApi {
    suspend fun opprett(
        ident: String,
        dokumenter: List<Dokument>,
        eksternReferanseId: String,
        tilleggsopplysninger: List<Pair<String, String>> = emptyList(),
        forsøkFerdigstill: Boolean = false,
        tittel: String? = null,
        sak: Sak? = null,
    ): JournalpostApiHttp.Resultat

    suspend fun opprett(
        forsøkFerdigstill: Boolean,
        journalpost: JournalpostApiHttp.Journalpost,
    ): JournalpostApiHttp.Resultat

    data class Dokument(
        val brevkode: String?,
        val tittel: String? = null,
        val varianter: List<Variant>,
    )

    data class Variant(
        val filtype: Filtype,
        val format: Format,
        val fysiskDokument: ByteArray,
    ) {
        override fun toString() =
            "Variant(filtype=$filtype, format=$format, størrelse=${prettyPrintFileSize(fysiskDokument.size.toLong())} byte)"

        enum class Filtype {
            PDF,
            PDFA,
            JPEG,
            TIFF,
            JSON,
            PNG,
        }

        enum class Format {
            ARKIV,
            ORIGINAL,
            FULLVERSJON,
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

        fun prettyPrintFileSize(size: Long): String {
            if (size <= 0) return "0 B"

            val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
            return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
        }
    }
}
