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
    api(project(":Mod_Repo"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:${project.property("kotlin")!!}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")

    implementation(fileTree("../libs") {
        include("**/*.jar")
        exclude("TinyLog")
    })

    // JSON
    val moshiVer = "1.12.0"
    api("com.squareup.moshi:moshi-kotlin:$moshiVer")
//    kapt("com.squareup.moshi:moshi-kotlin:$moshiVer")
    implementation("com.squareup.moshi:moshi-adapters:$moshiVer")
    // CSV
    implementation("org.apache.commons:commons-csv:1.8")
    // API
    api("io.ktor:ktor-client-core:1.6.4")
    api("io.ktor:ktor-client-cio:1.6.4")
    api("io.ktor:ktor-client-logging:1.6.4")

    // Version Checker Dependencies
    implementation("de.siegmar:fastcsv:2.1.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")

    // To calculate checksum for archive files
    // https://mvnrepository.com/artifact/commons-codec/commons-codec
    implementation("commons-codec:commons-codec:1.15")

    // Arrow
    implementation(project.property("arrowCore")!!)
    implementation(project.property("arrowOptics")!!)
    implementation(project.property("arrowFxCoroutines")!!)

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "16"
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