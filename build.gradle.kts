import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id("org.jmailen.kotlinter") version "4.1.0"
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
    implementation(RapidAndRiversKtor2)

    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.10.22-09.05.6fcf3395aa4f")
    implementation(Ktor2.Client.library("core"))
    implementation(Ktor2.Client.library("cio"))
    implementation(Ktor2.Client.library("content-negotiation"))
    implementation(Ktor2.Client.library("logging"))
    implementation("io.ktor:ktor-serialization-jackson:${Ktor2.version}")
    implementation(Kotlin.Coroutines.module("slf4j"))

    implementation("de.slub-dresden:urnlib:2.0.1")

    implementation(Mockk.mockk)

    testImplementation(kotlin("test"))
    testImplementation(Ktor2.Client.library("mock"))
    testImplementation(Junit5.library("params"))
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
