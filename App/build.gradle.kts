/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    alias(libs.plugins.compose)
}

group = "com.wisp"
// TODO don't forget to change default channel to "stable" in AppConfig for release. (jk lol what is release)
val smolVersion = "1.0.0-beta16"

// This gets appended to the app's jarfile, which means it has a unique name each time the app updates,
// resulting in the file not getting removed. Keep a constant version here so user doesn't end up with a ton of outdated files.
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val jcefFolder = "jcef-v1.0.18"
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.components:components-splitpane-desktop:1.0.1")
    implementation(libs.kotlin.reflect)
    implementation(libs.coroutines)
    implementation(libs.coroutines.swing)
    implementation(fileTree("../libs") { include("**/*.jar") })
    implementation(fileTree("libs") { include("**/*.jar") })
    implementation(project(":SMOL_Access"))
    implementation(project(":VRAM_Checker"))
    implementation(project(":UpdateStager"))
    implementation(project(":UpdateInstaller"))
    implementation(project(":Utilities"))

    // Logging
    implementation("org.tinylog:tinylog-api-kotlin:2.5.0")
    implementation("org.tinylog:tinylog-impl:2.6.2")

    // Modifying mod pages
    implementation("org.jsoup:jsoup:1.16.1")

    // Gson
    implementation(libs.kotson)

    // CLI builder, Clikt
//    implementation("com.github.ajalt.clikt:clikt:3.5.1")

    // CLI builder, Kotlin
//    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")

    // Fuzzy Search - not used
    implementation("com.github.android-password-store:sublime-fuzzy:2.3.4")
//    implementation("me.xdrop:fuzzywuzzy:1.4.0") // This one not used

    // List diffing
    implementation("dev.andrewbailey.difference:difference:1.0.0")

    // Markdown
    implementation("com.mikepenz:multiplatform-markdown-renderer-jvm:0.8.0")

    // Navigation
    val decomposeVer = "0.3.1"
    api("com.arkivanov.decompose:decompose:$decomposeVer")
    api("com.arkivanov.decompose:extensions-compose-jetbrains:$decomposeVer")

    // Image loading
    implementation("com.alialbaali.kamel:kamel-image:0.4.1")

    // Unit testing
    testImplementation(kotlin("test"))
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")

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
        jvmTarget = "${project.property("smolJvmTarget")}"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

tasks.test {
    useJUnitPlatform()
}

enum class Platform {
    Windows,
    MacOS,
    Linux
}

compose.desktop {
    application {
        mainClass = "smol.app.MainKt"

        val currentPlatform: Platform = run {
            val os = System.getProperty("os.name").lowercase()

            if (os.contains("mac") || os.contains("darwin")) {
                if ("aarch64" == System.getProperty("os.arch"))
                    Platform.MacOS
                else Platform.MacOS
            } else if (os.contains("windows"))
                Platform.Windows
            else if (os.contains("nux") || os.contains("nix"))
                Platform.Linux
            else throw RuntimeException(
                "Unsupported platform: $os"
            )
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            outputBaseDir.set(rootProject.projectDir.resolve("dist"))
            packageName = "SMOL"
            packageVersion = "1.0.0"
            description = "Starsector Mod Organizer and Launcher"
            vendor = "Wisp"
            licenseFile.set(project.file("../LICENSE.txt"))
            println("Project path: ${project.projectDir.absolutePath}")

            windows {
                if (currentPlatform == Platform.Windows) {
                    println("OS: Windows")
                    console = false
                    upgradeUuid = "51169f8d-9aec-4abf-b30a-f5bc5a5f6509"
                    iconFile.set(project.file("smol.ico"))
                    jvmArgs += listOf("-Djava.library.path=./libs/$jcefFolder/bin/lib/win64") // For CEF (Chromium Embedded Framework)
//                jvmArgs += listOf(
//                    "-XX:StartFlightRecording:settings=default,filename=./compose-rec.jfr",
//                    "-XX:FlightRecorderOptions:stackdepth=256"
//                )
//                jvmArgs += listOf("-Djava.library.path=native/windows") // To use lwjgl in VRAM Checker
                }
            }
            macOS {
                if (currentPlatform == Platform.MacOS) {
                    println("OS: MacOS")
                    iconFile.set(project.file("icon_png.png"))
                    bundleID = "org.wisp.smol"
                    jvmArgs += listOf("-Djava.library.path=native/macosx") // To use lwjgl in VRAM Checker}
                    println("Entitlements file exists? ${project.file("entitlements.plist").exists()}")
                    entitlementsFile.set(project.file("entitlements.plist"))
                    runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
                }
            }
            linux {
                if (currentPlatform == Platform.Linux) {
                    println("OS: Linux")
                    iconFile.set(project.file("icon_png.png"))
                    jvmArgs += listOf("-Djava.library.path=./libs/$jcefFolder/bin/lib/linux64")
                }
            }

            // task suggestRuntimeModules to generate this
            modules(
                "java.instrument",
                "java.management",
                "java.prefs",
                "java.sql",
                "jdk.unsupported",
                "jdk.zipfs",
                "jdk.accessibility"
            )
//            includeAllModules = true

            val resources = project.projectDir.resolve("resources")
            if (resources.exists()) {
                appResourcesRootDir.set(resources)
                System.out.println("Set resource path to ${resources.absolutePath}.")
            } else {
                System.err.println("Unable to find ${resources.absolutePath}.")
            }
        }

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

tasks.register("generateVersionProperties") {
    doLast {
        val propertiesFile = file("resources/common/version.properties")
        propertiesFile.parentFile.mkdirs()
        propertiesFile.writeText("smol-version=$smolVersion")
    }
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

/**
 * [https://github.com/kirill-grouchnikov/artemis/blob/woodland/build.gradle.kts#L57]
 */
//configurations {
//    all {
//        resolutionStrategy.eachDependency {
//            if (requested.group == "org.jetbrains.skiko") {
//                useVersion("0.7.18")
//                because("Pin to version that has shader bindings")
//            }
//        }
//    }
//}

// Map Files to a known path
fun mapObfuscatedJarFile(file: File) =
    File("${project.buildDir}/tmp/obfuscated/${file.nameWithoutExtension}.min.jar")

kotlin.jvmToolchain(project.property("smolJvmTarget").toString().toInt())