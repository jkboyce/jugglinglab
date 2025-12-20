//
// build.gradle.kts
//
// Juggling Lab build file for use with the Gradle build system.
// - `gradlew run` to build and run with Swing UI
// - `gradlew run -PJLcompose` to build and run with Compose UI
// - `gradlew build` to build bin/JugglingLab.jar
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
//import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.shadowJar)
}

object Versions {
    const val ORTOOLS_VERSION = "9.4.1874"
    const val MULTIK_VERSION = "0.2.3"
    const val KSOUP_VERSION = "0.2.5"
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            //implementation(libs.androidx.lifecycle.viewmodelCompose)
            //implementation(libs.androidx.lifecycle.runtimeCompose)
            // Juggling Lab specific
            implementation("org.jetbrains.kotlinx:multik-default:${Versions.MULTIK_VERSION}")
            implementation("com.fleeksoft.ksoup:ksoup:${Versions.KSOUP_VERSION}")
            implementation(compose.materialIconsExtended)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            // Compose Multiplatform
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // Juggling Lab specific
            implementation("com.google.ortools:ortools-java:${Versions.ORTOOLS_VERSION}")
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        val isCompose = project.hasProperty("JLcompose")
        mainClass = "jugglinglab.JugglingLabKt"
        jvmArgs += listOf(
            "-Xss2048k",
            "-Dfile.encoding=UTF-8",
            "-DJL_run_as_bundle=true",
            "-DJL_compose_ui=$isCompose",
            "--enable-native-access=ALL-UNNAMED"
        )
        /*
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.jugglinglab"
            packageVersion = "1.6.7"
        }*/
    }
}

// Custom task to build a fat JAR for the JVM target
val shadowJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "Creates a fat JAR for the JVM target"

    // Include project's compiled classes
    val jvmTarget = kotlin.targets.getByName("jvm")
    val mainCompilation = jvmTarget.compilations.getByName("main")
    from(mainCompilation.output)

    // Define dependencies
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))

    // Configure the JAR attributes
    manifest.attributes["Main-Class"] = "jugglinglab.JugglingLabKt"
    archiveBaseName.set("JugglingLab")
    archiveVersion.set("")
    archiveClassifier.set("")
    destinationDirectory.set(file("${project.rootDir}/bin"))

    // Excludes
    exclude("com/google/ortools/Loader.class")
    exclude("**/ortools-darwin*")
    exclude("**/ortools-win32*")
    exclude("**/ortools-linux*")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    // Ensure native libs are unpacked before this runs
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

    into("${project.rootDir}/bin/ortools-lib")

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
