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
}

group = "com.wisp"
version = "1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${project.property("kotlin")!!}")
    implementation(project(":Utilities"))
    implementation(project(":VRAM_Checker"))
    implementation(project(":UpdateInstaller"))
    api(project(":Mod_Repo"))

    implementation(libs.kotlin.reflect)
    implementation(libs.coroutines)

    implementation(fileTree("../libs") {
        include("**/*.jar")
        exclude("TinyLog")
    })

    // CSV
    implementation("org.apache.commons:commons-csv:1.9.0")
    // API
    val ktorVersion = "2.3.4"
    api("io.ktor:ktor-client-core:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-logging:$ktorVersion")

    // Version Checker Dependencies
    implementation("de.siegmar:fastcsv:2.2.2")
    implementation(libs.jackson.core)

    // Save file reading
    implementation(libs.jackson.dataformat.xml)

    // To calculate checksum for archive files
    // https://mvnrepository.com/artifact/commons-codec/commons-codec
    implementation("commons-codec:commons-codec:1.16.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")

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
    // List of where resources (the "data" folder) are.
    resources.setSrcDirs(listOf("main/resources"))
}

kotlin.jvmToolchain(project.property("smolJvmTarget").toString().toInt())