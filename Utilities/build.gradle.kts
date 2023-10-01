import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.libsDirectory

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
    java
}

group = "com.wisp"
version = "1.10.1"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(fileTree("../libs") {
        include("**/HJson/*.jar")
    })
    api("org.jetbrains:annotations:23.0.0")

    // https://mvnrepository.com/artifact/commons-io/commons-io
    implementation("commons-io:commons-io:2.11.0")

    // Gson
    api(libs.kotson)
    api (libs.jackson.databind)
    api (libs.jackson.module.kotlin)

    implementation(libs.kotlin.reflect)
    implementation(libs.coroutines)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "9"
//        jvmTarget = "${project.property("smolJvmTarget")}"
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}


kotlin.sourceSets.main {
    // List of where your Kotlin source code is, if any.
    kotlin.setSrcDirs(listOf("main/kotlin"))
}

java.sourceSets.main {
    this.java.setSrcDirs(listOf("main/kotlin"))
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}