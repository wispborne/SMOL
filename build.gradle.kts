import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.21"
    kotlin("kapt") version "1.5.21"
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

    implementation(fileTree("libs") { include("*.jar") })
    val moshiVer = "1.12.0"
    kapt("com.squareup.moshi:moshi-kotlin-codegen:$moshiVer")
    implementation("com.squareup.moshi:moshi-adapters:$moshiVer")

    implementation("org.tinylog:jcl-tinylog:2.4.0-M1")
    implementation("org.tinylog:tinylog-impl:2.4.0-M1")
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