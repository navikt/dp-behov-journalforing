package no.nav.dagpenger.behov.journalforing.fillager

import de.slub.urn.URN

interface Fillager {
    suspend fun hentFil(
        urn: FilURN,
        eier: String,
    ): ByteArray
}

class FilURN(
    urn: String,
) {
    private lateinit var id: URN

    init {
        kotlin
            .runCatching {
                id = URN.rfc8141().parse(urn)
            }.onFailure {
                throw IllegalArgumentException(it)
            }
    }

    val fil = id.namespaceSpecificString().toString()

    override fun equals(other: Any?): Boolean {
        if (other !is FilURN) {
            return false
        }
        return this.fil == other.fil
    }

    override fun hashCode() = this.fil.hashCode()
}
