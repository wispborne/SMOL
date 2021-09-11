import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    val kotlinVer = "1.5.30"
    kotlin("jvm") version kotlinVer
    kotlin("kapt") version kotlinVer
    id("org.jetbrains.compose") version "1.0.0-alpha4-build344"
}

group = "com.wisp"
version = "1.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(kotlin("reflect"))

    implementation(fileTree("libs") { include("*.jar") })

    // JSON
    val moshiVer = "1.12.0"
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVer")
//    kapt("com.squareup.moshi:moshi-kotlin:$moshiVer")
    implementation("com.squareup.moshi:moshi-adapters:$moshiVer")

    // Logging
    implementation("org.tinylog:jcl-tinylog:2.4.0-M1")
    implementation("org.tinylog:tinylog-impl:2.4.0-M1")

    // Navigation
    val decomposeVer = "0.3.1"
    api("com.arkivanov.decompose:decompose:$decomposeVer")
    api("com.arkivanov.decompose:extensions-compose-jetbrains:$decomposeVer")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SMOL"
            packageVersion = "1.0.0"
        }
    }
}