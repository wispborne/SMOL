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
version = "1.10.1"

val lwjglVersion = "3.3.1"
val lwjglNatives = "natives-windows"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":Utilities"))

//    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
//    implementation("org.lwjgl", "lwjgl")
//    implementation("org.lwjgl", "lwjgl-opengl")
//    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
//    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
//    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation(project.property("coroutines")!!)

    implementation("de.siegmar:fastcsv:2.2.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4")

    // Hardware info
    api("com.github.oshi:oshi-core:6.3.0")
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