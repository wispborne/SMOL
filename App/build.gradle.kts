import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.0.0-beta5"
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

    implementation(fileTree("../libs") { include("**/*.jar") })
    implementation(project(":SMOL_Access"))
    implementation(project(":VRAM_Checker"))
    implementation(project(":Utilities"))

    // Gson
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

    // CLI builder, Clikt
    implementation("com.github.ajalt.clikt:clikt:3.3.0")

    // CLI builder, Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.3")

    // Fuzzy Search
    implementation("me.xdrop:fuzzywuzzy:1.3.1")

    // Navigation
    val decomposeVer = "0.3.1"
    api("com.arkivanov.decompose:decompose:$decomposeVer")
    api("com.arkivanov.decompose:extensions-compose-jetbrains:$decomposeVer")

    // Unit testing? ughhhhh
    testImplementation(kotlin("test"))
}

kotlin.sourceSets.main {
    // List of where your Kotlin source code is, if any.
    kotlin.setSrcDirs(listOf("main/kotlin"))
}
kotlin.sourceSets.test {
    kotlin.setSrcDirs(listOf("test/kotlin"))
}
java.sourceSets.main {
    // Doesn't work if run from the kotlin plugin, black magic
    resources.setSrcDirs(listOf(projectDir.resolve("main/resources")))
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "16"
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

tasks.test {
    useJUnitPlatform()
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
                jvmArgs += listOf("-Djava.library.path=native/windows") // To use lwjgl in VRAM Checker
            }
            macOS {
                jvmArgs += listOf("-Djava.library.path=native/macosx") // To use lwjgl in VRAM Checker
            }
            linux {
                jvmArgs += listOf("-Djava.library.path=native/linux") // To use lwjgl in VRAM Checker
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