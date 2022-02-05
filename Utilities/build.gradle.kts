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
    api("org.jetbrains:annotations:22.0.0")

    // https://mvnrepository.com/artifact/commons-io/commons-io
    implementation("commons-io:commons-io:2.11.0")

    // Gson
    api("com.github.salomonbrys.kotson:kotson:2.5.0")
    api ("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    api ("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

    implementation("org.jetbrains.kotlin:kotlin-reflect:${project.property("kotlin")!!}")
    implementation(project.property("coroutines")!!)
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

java.sourceSets.main {
    this.java.setSrcDirs(listOf("main/kotlin"))
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}