import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.0.1"
}

group = "com.wisp"
version = "1.0.0-prerelease3" // TODO don't forget to change default channel to "stable" in AppConfig for release.

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val jcefFolder = "jcef-v1.0.18"
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.components:components-splitpane-desktop:1.0.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation(project.property("coroutines")!!)

    implementation(fileTree("../libs") { include("**/*.jar") })
    implementation(fileTree("libs") { include("**/*.jar") })
    implementation(project(":SMOL_Access"))
    implementation(project(":VRAM_Checker"))
    implementation(project(":Utilities"))

    // Logging
    implementation("org.tinylog:tinylog-api-kotlin:2.4.1")
    implementation("org.tinylog:tinylog-impl:2.4.1")

    // Modifying mod pages
    implementation("org.jsoup:jsoup:1.14.3")

    // Gson
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

    // CLI builder, Clikt
    implementation("com.github.ajalt.clikt:clikt:3.3.0")

    // CLI builder, Kotlin
//    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")

    // Fuzzy Search - not used
    implementation("com.github.android-password-store:sublime-fuzzy:2.0.0")
    implementation("me.xdrop:fuzzywuzzy:1.3.1") // This one not used

    // List diffing
    implementation("dev.andrewbailey.difference:difference:1.0.0")

    // Auto-update
    implementation("org.update4j:update4j:1.5.8")

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
    java.setSrcDirs(listOf("main/kotlin"))
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
        mainClass = "smol_app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)
            outputBaseDir.set(project.projectDir.resolve("dist"))
            packageName = "SMOL"
            packageVersion = "1.0.0"
            val exePath = "main/app/SMOL/"
            val looseFiles = listOf("SMOL_Themes.json", "version.properties")

            windows {
                println("OS: Windows")
                console = true
                upgradeUuid = "51169f8d-9aec-4abf-b30a-f5bc5a5f6509"
                jvmArgs += listOf("-Djava.library.path=./libs/$jcefFolder/bin/lib/win64") // For CEF (Chromium Embedded Framework)
//                jvmArgs += listOf(
//                    "-XX:StartFlightRecording:settings=default,filename=./compose-rec.jfr",
//                    "-XX:FlightRecorderOptions:stackdepth=256"
//                )
//                jvmArgs += listOf("-Djava.library.path=native/windows") // To use lwjgl in VRAM Checker
            }
            macOS {
                println("OS: MacOS")
//                jvmArgs += listOf("-Djava.library.path=native/macosx") // To use lwjgl in VRAM Checker
            }
            linux {
                println("OS: Linux")
//                jvmArgs += listOf("-Djava.library.path=native/linux") // To use lwjgl in VRAM Checker
            }

            // task suggestRuntimeModules to generate this
            modules(
                "java.instrument",
                "java.management",
                "java.prefs",
                "java.sql",
                "jdk.unsupported",
                "jdk.zipfs"
            )
//            includeAllModules = true

            val appDir = outputBaseDir.get().asFile.resolve(exePath)
            looseFiles
                .map { projectDir.resolve(it) }
                .forEach {
                    if (it.exists()) {
                        it.copyTo(appDir.resolve(it.name), overwrite = true)
                    } else {
                        println("Couldn't find ${it.absolutePath}.")
                    }
                }
        }
    }
}

tasks.register("generateVersionProperties") {
    doLast {
        val propertiesFile = file("version.properties")
        propertiesFile.parentFile.mkdirs()
        propertiesFile.writeText("smol-version=$version")
    }
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}