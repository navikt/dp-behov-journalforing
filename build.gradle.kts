import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id("org.jmailen.kotlinter") version "3.9.0"
    kotlin("plugin.serialization") version Kotlin.version
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

application {
    mainClass.set("no.nav.dagpenger.behov.journalforing.AppKt")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    val ktor_version = "1.6.8"
    implementation(RapidAndRivers)

    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-serialization:$ktor_version")
    implementation("com.github.navikt.dp-biblioteker:aad-klient:2021.04.09-14.32.088c6dc10b69")

    implementation("de.slub-dresden:urnlib:2.0.1")

    implementation(Mockk.mockk)

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:$ktor_version")
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
