plugins {
    kotlin("jvm")
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
    implementation(fileTree("libs") {
        include("**/*.jar")
        exclude("TinyLog")
    })

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation(project.property("coroutines")!!)

    implementation("de.siegmar:fastcsv:2.1.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")

    // Hardware info
    api("com.github.oshi:oshi-core:5.8.3")
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
}