plugins {
    kotlin("jvm")
    application
}

group = "com.wisp"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // Auto-update
    implementation("org.update4j:update4j:1.5.8")
}

application {
    mainClass.set("update_installer.Main")
}

// Create uber jar with all dependencies inside.
tasks.register(name = "uberJar", type = Jar::class) {
    archiveFileName.set("${project.name}-fat.jar")
    destinationDirectory.set(File("dist"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Implementation-Title"] = "SMOL Update Installer"
        attributes["Implementation-Version"] = "1.0.0"
        attributes["Main-Class"] = "update_installer.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "9"
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

kotlin.sourceSets.main {
    // List of where your Kotlin source code is, if any.
    kotlin.setSrcDirs(listOf("main/kotlin"))
}