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

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}
rootProject.name = "SMOL"
include("App", "SMOL_Access", "VRAM_Checker", "UpdateStager", "Utilities", "Mod_Repo", "UpdateInstaller")


dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            plugin("compose", "org.jetbrains.compose").version("1.5.10")

            library("kotlin-reflect", "org.jetbrains.kotlin:kotlin-reflect:1.9.0")
            library("kotlin-reflect", "org.jetbrains.kotlin:kotlin-reflect:1.9.0")
            library("kotson", "com.github.salomonbrys.kotson:kotson:2.5.0")
            library("coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-native-mt")
            library("coroutines-swing", "org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.0")
            library("jackson-core", "com.fasterxml.jackson.core:jackson-core:2.15.2")
            library("jackson-dataformat-xml", "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.15.2")
            library("jackson-databind", "com.fasterxml.jackson.core:jackson-databind:2.15.2")
            library("jackson-module-kotlin", "com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
            library("update4j", "org.update4j:update4j:1.5.9")
        }
    }
}