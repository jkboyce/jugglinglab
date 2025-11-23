//
// build.gradle.kts
//
// Juggling Lab build file for use with the Gradle build system.
// - `gradlew build` to build JugglingLab.jar
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.2.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1" // For creating the fat JAR
}

group = "org.jugglinglab"
version = "1.6.7"

repositories {
    mavenCentral()
}

// Define versions in one place
object Versions {
    const val junit = "5.10.0"
    const val commonsMath = "3.6.1"
    const val orTools = "9.4.1874"
}

dependencies {
    // Kotlin Standard Library
    implementation(kotlin("stdlib"))

    // Application Dependencies
    implementation("org.apache.commons:commons-math3:${Versions.commonsMath}")
    implementation("com.google.ortools:ortools-java:${Versions.orTools}")

    // Testing Dependencies
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.junit}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Versions.junit}")
}

application {
    // Define the main class for the application
    mainClass.set("jugglinglab.JugglingLabKt")
}

java {
    // Set the Java version for the project
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Test> {
    // Enable the JUnit 5 platform for running tests
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    // Configure the fat JAR (equivalent to maven-shade-plugin)
    archiveBaseName.set("JugglingLab")
    archiveVersion.set("")
    archiveClassifier.set("")
    destinationDirectory.set(file("${project.projectDir}/bin"))

    // Exclude the native OR-Tools libraries from the fat JAR
    configurations = listOf(project.configurations.runtimeClasspath.get())
    exclude("com/google/ortools/Loader.class")
    exclude("**/ortools-darwin*")
    exclude("**/ortools-win32*")
    exclude("**/ortools-linux*")

    // Remove signature files to prevent security exceptions
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Custom task to unpack the OR-Tools native libraries
val unpackOrtNatives by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Unpacks OR-Tools native libraries to the bin/ortools-lib directory."

    val ortConfiguration = configurations.create("ortNatives") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies {
        // Define the native artifacts to download
        "ortNatives"("com.google.ortools:ortools-darwin-aarch64:${Versions.orTools}")
        "ortNatives"("com.google.ortools:ortools-darwin-x86-64:${Versions.orTools}")
        "ortNatives"("com.google.ortools:ortools-linux-aarch64:${Versions.orTools}")
        "ortNatives"("com.google.ortools:ortools-linux-x86-64:${Versions.orTools}")
        "ortNatives"("com.google.ortools:ortools-win32-x86-64:${Versions.orTools}")
    }

    into("${project.projectDir}/bin/ortools-lib")

    // For each artifact in the configuration, unpack it into its own subdirectory
    ortConfiguration.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
        // From the zip, include only the directory matching the artifact name
        from(zipTree(artifact.file)) {
            include("${artifact.name}/**")
        }
    }
}

// Make sure the natives are unpacked when the shadow JAR is built
tasks.shadowJar {
    dependsOn(unpackOrtNatives)
}