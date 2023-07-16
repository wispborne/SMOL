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

plugins {
    kotlin("jvm")
    application
}

group = "com.wisp"
version = "1.10.1"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":Utilities"))
    implementation(project(":SMOL_Access"))
    implementation(project(":UpdateInstaller"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation(project.property("coroutines")!!)

    // Auto-update
    api("org.update4j:update4j:1.5.9")
}

application {
    mainClass.set("smol.updatestager.Main")
}

fun JavaExec.configureRun(channel: String) {
    val directoryOfFilesToAddToManifest = rootDir.resolve("dist/main/app/SMOL")
    this.setArgsString("${directoryOfFilesToAddToManifest.absolutePath} $channel")
}

tasks.withType<JavaExec>().configureEach {
}

tasks {
    val run by existing(JavaExec::class)

    register("runStable") {
        doFirst {
            run.configure { this.configureRun("stable") }
        }
        finalizedBy("run")
    }

    register("runUnstable") {
        doFirst {
            run.configure { this.configureRun("unstable") }
        }
        finalizedBy("run")
    }

    register("runTest") {
        doFirst {
            run.configure { this.configureRun("test") }
        }
        finalizedBy("run")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "${project.property("smolJvmTarget")}"
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}


kotlin.sourceSets.main {
    // List of where your Kotlin source code is, if any.
    kotlin.setSrcDirs(listOf("main/kotlin"))
}
kotlin.jvmToolchain(project.property("smolJvmTarget").toString().toInt())