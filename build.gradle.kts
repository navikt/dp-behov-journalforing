import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version "1.9.24"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    kotlin("plugin.serialization") version "1.9.24"
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
    implementation("no.nav.dagpenger:oauth2-klient:2024.10.31-15.02.1d4f08a38d24")
    implementation(libs.bundles.ktor.client)
    implementation("io.ktor:ktor-serialization-jackson:${libs.versions.ktor.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.9.0")
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
