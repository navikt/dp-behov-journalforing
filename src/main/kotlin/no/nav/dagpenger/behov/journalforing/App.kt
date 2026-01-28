package no.nav.dagpenger.behov.journalforing

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.dagpenger.behov.journalforing.Configuration.dokarkivTokenProvider
import no.nav.dagpenger.behov.journalforing.Configuration.dpSøknadTokenProvider
import no.nav.dagpenger.behov.journalforing.Configuration.mellomlagringTokenProvider
import no.nav.dagpenger.behov.journalforing.fillager.FillagerHttp
import no.nav.dagpenger.behov.journalforing.journalpost.JournalpostApiHttp
import no.nav.dagpenger.behov.journalforing.soknad.SoknadHttp
import no.nav.dagpenger.behov.journalforing.tjenester.JournalforingBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.JournalførEttersendingBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.JournalførSøknadPdfOgVedleggBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.MeldekortJournalføringBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.MinidialogJournalføringBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.RapporteringJournalføringBehovLøser
import no.nav.dagpenger.behov.journalforing.tjenester.VedtaksbrevJournalføringBehovløser
import no.nav.helse.rapids_rivers.RapidApplication

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
            JournalførSøknadPdfOgVedleggBehovLøser(
                rapidsConnection = it,
                fillager = fillager,
                journalpostApi = journalpostApi,
            )
            JournalførEttersendingBehovLøser(
                rapidsConnection = it,
                fillager = fillager,
                journalpostApi = journalpostApi,
            )
            MinidialogJournalføringBehovLøser(
                rapidsConnection = it,
                journalpostApi = journalpostApi,
            )
            RapporteringJournalføringBehovLøser(
                rapidsConnection = it,
                journalpostApi = journalpostApi,
            )
            MeldekortJournalføringBehovLøser(
                rapidsConnection = it,
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
