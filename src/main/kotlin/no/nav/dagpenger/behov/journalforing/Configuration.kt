package no.nav.dagpenger.behov.journalforing

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config

internal object Configuration {
    private const val appName = "dp-behov-journalforing"
    private val defaultProperties = ConfigurationMap(
        mapOf(
            "RAPID_APP_NAME" to appName,
            "KAFKA_CONSUMER_GROUP_ID" to "$appName-v1",
            "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
            "KAFKA_RESET_POLICY" to "latest",
            "DOKARKIV_SCOPE" to "api://dev-fss.teamdokumenthandtering.dokarkiv-q1/.default",
            "DOKARKIV_INGRESS" to "dokarkiv.dev-fss-pub.nais.io",
            "MELLOMLAGRING_SCOPE" to "api://dev-gcp.teamdagpenger.dp-mellomlagring/.default",
            "DP_SOKNAD_SCOPE" to "api://dev-gcp.teamdagpenger.dp-soknad/.default"
        )
    )
    private val prodProperties = ConfigurationMap(
        mapOf(
            "DOKARKIV_SCOPE" to "api://prod-fss.teamdokumenthandtering.dokarkiv/.default",
            "MELLOMLAGRING_SCOPE" to "api://prod-gcp.teamdagpenger.dp-mellomlagring/.default",
            "DP_SOKNAD_SCOPE" to "api://prod-gcp.teamdagpenger.dp-soknad/.default"
        )
    )
    val properties: Configuration by lazy {
        val systemAndEnvProperties = ConfigurationProperties.systemProperties() overriding EnvironmentVariables()
        when (System.getenv().getOrDefault("NAIS_CLUSTER_NAME", "LOCAL")) {
            "prod-gcp" -> systemAndEnvProperties overriding prodProperties overriding defaultProperties
            else -> systemAndEnvProperties overriding defaultProperties
        }
    }
    val dokarkivTokenProvider by lazy {
        azureAdTokenSupplier(properties[Key("DOKARKIV_SCOPE", stringType)])
    }

    val mellomlagringTokenProvider by lazy {
        azureAdTokenSupplier(properties[Key("MELLOMLAGRING_SCOPE", stringType)])
    }
    val dpSøknadTokenProvider by lazy {
        azureAdTokenSupplier(properties[Key("DP_SOKNAD_SCOPE", stringType)])
    }

    val config: Map<String, String> = properties.list().reversed().fold(emptyMap()) { map, pair ->
        map + pair.second
    }

    private val azureAdClient: CachedOauth2Client by lazy {
        val azureAdConfig = OAuth2Config.AzureAd(properties)
        CachedOauth2Client(
            tokenEndpointUrl = azureAdConfig.tokenEndpointUrl,
            authType = azureAdConfig.clientSecret()
        )
    }

    private fun azureAdTokenSupplier(scope: String): () -> String = {
        runBlocking { azureAdClient.clientCredentials(scope).accessToken }
    }
}
