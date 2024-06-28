package no.nav.dagpenger.behov.journalforing

import no.nav.dagpenger.behov.journalforing.Configuration.dokarkivTokenProvider
import no.nav.dagpenger.behov.journalforing.Configuration.dpSøknadTokenProvider
import no.nav.dagpenger.behov.journalforing.Configuration.mellomlagringTokenProvider
import no.nav.dagpenger.behov.journalforing.fillager.FillagerHttp
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp
import no.nav.dagpenger.behov.journalforing.soknad.SoknadHttp
import no.nav.dagpenger.behov.journalforing.tjenester.JournalforingBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.RapporteringJournalføringBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.VedtaksbrevJournalføringBehovløser
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

fun main() {
    App.start()
}

internal object App : RapidsConnection.StatusListener {
    private val fillager = FillagerHttp(tokenProvider = mellomlagringTokenProvider)
    private val journalpostApi = JournalpostApiHttp(tokenProvider = dokarkivTokenProvider)

    private val rapidsConnection =
        RapidApplication.create(Configuration.config).also {
            JournalforingBehovLøser(
                rapidsConnection = it,
                fillager = fillager,
                journalpostApi = journalpostApi,
                faktahenter = SoknadHttp(tokenProvider = dpSøknadTokenProvider),
            )
            RapporteringJournalføringBehovLøser(
                rapidsConnection = it,
                fillager = fillager,
                journalpostApi = journalpostApi,
            )
            VedtaksbrevJournalføringBehovløser(
                rapidsConnection = it,
                fillager = fillager,
                journalpostApi = journalpostApi,
            )
        }

    init {
        rapidsConnection.register(this)
    }

    fun start() = rapidsConnection.start()
}
