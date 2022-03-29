package no.nav.dagpenger.behov.journalforing

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.overriding

internal object Configuration {
    const val appName = "dp-behov-journalforing"
    private val defaultProperties = ConfigurationMap(
        mapOf(
            "RAPID_APP_NAME" to appName,
            "KAFKA_CONSUMER_GROUP_ID" to "$appName-v1",
            "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
            "KAFKA_RESET_POLICY" to "latest",
        )
    )
    val properties: Configuration =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties
    val config: Map<String, String> = properties.list().reversed().fold(emptyMap()) { map, pair ->
        map + pair.second
    }
}
