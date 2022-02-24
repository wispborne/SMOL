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
    kotlin("jvm") version "1.6.10"
}

group = "com.wisp"
version = "1.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation(project.property("coroutines")!!)
}

buildscript {
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.1.0")
    }
}

tasks.withType<proguard.gradle.ProGuardTask>().configureEach {
    configuration("../proguard.pro")
    dontoptimize()
}

tasks.register("buildSmol") {
    dependsOn("App:createDistributable", "UpdateInstaller:uberJar")

    doLast {
        copy {
            val sourceDist = rootProject.projectDir.resolve("UpdateInstaller/dist")
            val sourceJre = rootProject.projectDir.resolve("UpdateInstaller/jre")
            val dest = rootProject.projectDir.resolve("dist/main/app/SMOL")
            println("Copying from $sourceDist, $sourceJre to $dest.")
            from(sourceDist, sourceJre)
            into(dest)
        }
    }
}

tasks.register("buildSmolDist") {
    dependsOn("buildSmol", "UpdateStager:run")
}