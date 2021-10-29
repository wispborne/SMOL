import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.5.21"
    id("org.jetbrains.compose") version "1.0.0-alpha3"
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
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")

    implementation(fileTree("libs") { include("**/*.jar") })
    implementation(project("SMOL_Access"))

    // JSON
//    val moshiVer = "1.12.0"
//    implementation("com.squareup.moshi:moshi-kotlin:$moshiVer")
//    kapt("com.squareup.moshi:moshi-kotlin:$moshiVer")
//    implementation("com.squareup.moshi:moshi-adapters:$moshiVer")

    // CLI builder, Clikt
    implementation("com.github.ajalt.clikt:clikt:3.3.0")

    // Arrow
//    implementation(project.property("arrowCore")!!)
//    implementation(project.property("arrowOptics")!!)

    // Navigation
    val decomposeVer = "0.3.1"
    api("com.arkivanov.decompose:decompose:$decomposeVer")
    api("com.arkivanov.decompose:extensions-compose-jetbrains:$decomposeVer")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "16"
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)
            outputBaseDir.set(project.projectDir.resolve("dist"))
            packageName = "SMOL"
            packageVersion = "1.0.0"
            windows {
                console = true
                upgradeUuid = "51169f8d-9aec-4abf-b30a-f5bc5a5f6509"
            }

            // task suggestRuntimeModules to generate this
            modules(
                "java.instrument",
                "java.management",
                "java.prefs",
                "java.sql",
                "jdk.unsupported"
            )
//            includeAllModules = true
        }
    }
}