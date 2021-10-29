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
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")

    implementation(fileTree("../libs") { include("**/*.jar") })

    // JSON
    val moshiVer = "1.12.0"
    api("com.squareup.moshi:moshi-kotlin:$moshiVer")
//    kapt("com.squareup.moshi:moshi-kotlin:$moshiVer")
    implementation("com.squareup.moshi:moshi-adapters:$moshiVer")
    // Gson
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")
    // CSV
    implementation("org.apache.commons:commons-csv:1.8")

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