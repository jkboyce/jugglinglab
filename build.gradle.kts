//
// build.gradle.kts
//
// Juggling Lab build file for use with the Gradle build system.
// - `gradlew build` to build bin/JugglingLab.jar
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("unused")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.0-RC"
    id("com.github.johnrengelman.shadow") version "8.1.1" // For creating the fat JAR
}

group = "org.jugglinglab"
version = "1.6.7"

repositories {
    mavenCentral()
}

// Define versions in one place
object Versions {
    const val COMMONS_MATH_VERSION = "3.6.1"
    const val ORTOOLS_VERSION = "9.4.1874"
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Common dependencies go here. Initially this is empty.
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                // JVM-specific dependencies are moved here
                implementation("org.apache.commons:commons-math3:${Versions.COMMONS_MATH_VERSION}")
                implementation("com.google.ortools:ortools-java:${Versions.ORTOOLS_VERSION}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

    jvmToolchain(21)
}

// Register the ShadowJar task manually (required for KMP)
val shadowJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "Creates a fat JAR for the JVM target"

    // 1. Include project's compiled classes
    val jvmTarget = kotlin.targets.getByName("jvm")
    val mainCompilation = jvmTarget.compilations.getByName("main")
    from(mainCompilation.output)

    // 2. Define dependencies
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))

    // 3. Configure the JAR attributes
    manifest.attributes["Main-Class"] = "jugglinglab.JugglingLabKt"
    archiveBaseName.set("JugglingLab")
    archiveVersion.set("")
    archiveClassifier.set("")
    destinationDirectory.set(file("${project.projectDir}/bin"))

    // 4. Excludes
    exclude("com/google/ortools/Loader.class")
    exclude("**/ortools-darwin*")
    exclude("**/ortools-win32*")
    exclude("**/ortools-linux*")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    // 5. Ensure native libs are unpacked before this runs
    dependsOn(unpackOrtNatives)
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
        "ortNatives"("com.google.ortools:ortools-darwin-aarch64:${Versions.ORTOOLS_VERSION}")
        "ortNatives"("com.google.ortools:ortools-darwin-x86-64:${Versions.ORTOOLS_VERSION}")
        "ortNatives"("com.google.ortools:ortools-linux-aarch64:${Versions.ORTOOLS_VERSION}")
        "ortNatives"("com.google.ortools:ortools-linux-x86-64:${Versions.ORTOOLS_VERSION}")
        "ortNatives"("com.google.ortools:ortools-win32-x86-64:${Versions.ORTOOLS_VERSION}")
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

// Ensure the `build` task also creates the final executable JAR
tasks.named("build") {
    dependsOn(shadowJar)
}