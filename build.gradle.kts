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

tasks.register("buildSmolDist") {
    dependsOn("App:createDistributable", "UpdateStager:run", "UpdateInstaller:uberJar")
    doLast {
        copy {
            val source = rootProject.projectDir.resolve("UpdateInstaller/dist")
            val dest = rootProject.projectDir.resolve("dist/main/app/SMOL")
            println("Copying from $source to $dest.")
            from(source)
            into(dest)
        }
    }
}