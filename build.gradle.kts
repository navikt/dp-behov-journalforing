import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version "2.1.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    kotlin("plugin.serialization") version "2.1.20"
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    maven("https://jitpack.io")
}

application {
    mainClass.set("no.nav.dagpenger.behov.journalforing.AppKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.rapids.and.rivers)

    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation("no.nav.dagpenger:oauth2-klient:2025.04.26-14.51.bbf9ece5f5ec")
    implementation(libs.bundles.ktor.client)
    implementation("io.ktor:ktor-serialization-jackson:${libs.versions.ktor.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.2")
    implementation("de.slub-dresden:urnlib:2.0.1")

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation("org.junit.jupiter:junit-jupiter-params:${libs.versions.junit.get()}")
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
