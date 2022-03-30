package no.nav.dagpenger.behov.journalforing

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.aad.api.ClientCredentialsClient

internal object Configuration {
    const val appName = "dp-behov-journalforing"
    private val defaultProperties = ConfigurationMap(
        mapOf(
            "RAPID_APP_NAME" to appName,
            "KAFKA_CONSUMER_GROUP_ID" to "$appName-v1",
            "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
            "KAFKA_RESET_POLICY" to "latest",
            "DP_PROXY_SCOPE" to "api://dev-fss.teamdagpenger.dp-proxy/.default",
            "DP_PROXY_URL" to "https://dp-proxy.dev-fss-pub.nais.io",
        )
    )

    private val prodProperties = ConfigurationMap(
        mapOf(
            "DP_PROXY_SCOPE" to "api://prod-fss.teamdagpenger.dp-proxy/.default",
            "DP_PROXY_URL" to "https://dp-proxy.prod-fss-pub.nais.io",
        )
    )

    val properties: Configuration by lazy {
        val systemAndEnvProperties = ConfigurationProperties.systemProperties() overriding EnvironmentVariables()
        when (System.getenv().getOrDefault("NAIS_CLUSTER_NAME", "LOCAL")) {
            "prod-gcp" -> systemAndEnvProperties overriding prodProperties overriding defaultProperties
            else -> systemAndEnvProperties overriding defaultProperties
        }
    }

    val dpProxyTokenProvider by lazy {
        ClientCredentialsClient(properties) {
            scope {
                add(properties[Key("DP_PROXY_SCOPE", stringType)])
            }
        }
    }

    val config: Map<String, String> = properties.list().reversed().fold(emptyMap()) { map, pair ->
        map + pair.second
    }
}
