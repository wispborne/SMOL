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
    kotlin("jvm") version "1.7.20" // Also update down at the bottom of this file.
    id("dev.hydraulic.conveyor") version "1.2"
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


//tasks.withType<proguard.gradle.ProGuardTask>().configureEach {
//    configuration("../proguard.pro")
//    dontoptimize()
//}

tasks.register("buildSmol") {
    dependsOn("App:createDistributable")
//    dependsOn("App:createDistributable", "UpdateInstaller:uberJar")
    // ProGuard
//    dependsOn("App:createReleaseDistributable", "UpdateInstaller:uberJar")

    doLast {
        copy {
            val sources = arrayOf(
//                rootProject.projectDir.resolve("UpdateInstaller/dist"),
//                    rootProject.projectDir.resolve("UpdateInstaller/jre"),
                rootProject.projectDir.resolve("README.md"),
                rootProject.projectDir.resolve("LICENSE.txt"),
//                    rootProject.projectDir.resolve("UpdateInstaller/install-SMOL-update.bat"),
            )

            val dest = rootProject.projectDir.resolve("dist/main/app/SMOL")
            println("Copying from ${sources.joinToString()} to $dest.")
            from(*sources)
            into(dest)
        }
    }
}

tasks.register("buildSmolDistStable") {
    dependsOn("buildSmol")
//    dependsOn("buildSmol", "UpdateStager:runStable")
}
tasks.register("buildSmolDistUnstable") {
    dependsOn("buildSmol")
//    dependsOn("buildSmol", "UpdateStager:runUnstable")
}
tasks.register("buildSmolDistTest") {
    dependsOn("buildSmol")
//    dependsOn("buildSmol", "UpdateStager:runTest")
}


// Conveyor Stuff
// region Work around temporary Compose bugs.
configurations.all {
    attributes {
        // https://github.com/JetBrains/compose-jb/issues/1404#issuecomment-1146894731
        attribute(Attribute.of("ui", String::class.java), "awt")
    }
}

// Force override the Kotlin stdlib version used by Compose to 1.7, as otherwise we can end up with a mix of 1.6 and 1.7 on our classpath.
dependencies {
    val v = "1.7.20"
    for (m in setOf("linuxAmd64", "macAmd64", "macAarch64", "windowsAmd64")) {
        m("org.jetbrains.kotlin:kotlin-stdlib:$v")
        m("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$v")
        m("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$v")
    }
}
// endregion