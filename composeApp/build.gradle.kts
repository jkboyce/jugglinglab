//
// build.gradle.kts
//
// Juggling Lab build file for use with the Gradle build system.
// A few useful commands:
//
// - `gradlew run` to build and run the desktop app
// - `gradlew run -PJLcompose` to build and run the desktop app with Compose UI
// - `gradlew desktopBuild` to build bin/JugglingLab.jar
// - `gradlew androidBuild` to build Android Debug APKs
// - `gradlew :androidApp:bundleRelease` to build a signed AAB for release
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.antlrKotlin)
}

group = "org.jugglinglab"

kotlin {
    android {
        namespace = "org.jugglinglab.composelibrary"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }
    }

    jvm()

    sourceSets {
        commonMain {
            kotlin {
                srcDir(provider { tasks.named("generateKotlinGrammarSource").get() })
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Juggling Lab specific
            implementation(libs.fleeksoft.ksoup)
            implementation(libs.antlr.kotlin.runtime)
            implementation(libs.compose.material.icons.extended)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.jetbrains.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // Juggling Lab specific
            implementation(libs.firebase.crashlytics)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // Juggling Lab specific
            implementation(libs.google.ortools)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

compose.desktop {
    application {
        val isCompose = project.hasProperty("JLcompose")
        mainClass = "org.jugglinglab.MainKt"

        jvmArgs += listOf(
            "-Xss2048k",
            "-Dfile.encoding=UTF-8",
            "-DJL_run_as_bundle=true",
            "-DJL_compose_ui=$isCompose",
            "--enable-native-access=ALL-UNNAMED"
        )

        /*
        nativeDistributions {
            // this section is unused; native packaging scripts are in bin/packaging

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Juggling Lab"
            packageVersion = "1.7.3"

            macOS {
                //iconFile.set(project.file("launcher-icons/icon.icns"))
                dockName = "Juggling Lab"
            }
            windows {
                //iconFile.set(project.file("launcher-icons/icon.ico"))
            }
            linux {
                //iconFile.set(project.file("launcher-icons/icon.png"))
            }
        }
        */
    }
}

//------------------------------------------------------------------------------
// Custom tasks
//------------------------------------------------------------------------------

// Custom task to generate ANTLR sources for siteswap parser
val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    description = "Generates siteswap parser from ANTLR4 sources"

    // ANTLR .g4 files are under composeApp/antlr
    source = fileTree(layout.projectDirectory.dir("antlr")) {
        include("**/*.g4")
    }

    // package name for generated source files
    val pkgName = "org.jugglinglab.notation.ssparser.generated"
    packageName = pkgName

    // we want visitors alongside listeners
    arguments = listOf("-visitor")

    // generated files are outputted in build/generatedAntlr/{package-name}
    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

// Custom task to build a fat JAR for the JVM target
val shadowJar by tasks.existing(ShadowJar::class) {
    group = "build"
    description = "Creates a fat JAR for the JVM target"

    // Include project's compiled classes
    val jvmTarget = kotlin.targets.getByName("jvm")
    val mainCompilation = jvmTarget.compilations.getByName("main")
    from(mainCompilation.output)

    // Define dependencies
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))

    // Configure the JAR attributes
    manifest.attributes["Main-Class"] = "org.jugglinglab.MainKt"
    archiveBaseName.set("JugglingLab")
    archiveVersion.set("")
    archiveClassifier.set("")
    destinationDirectory.set(file("${project.rootDir}/bin"))

    // Exclude several unneeded things from the JAR

    // OR-Tools binaries
    exclude("com/google/ortools/Loader.class")
    exclude("**/ortools-darwin*/**")
    exclude("**/ortools-win32*/**")
    exclude("**/ortools-linux*/**")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    // Unused Material Icons themes
    exclude("androidx/compose/material/icons/twotone/**")
    exclude("androidx/compose/material/icons/rounded/**")
    exclude("androidx/compose/material/icons/outlined/**")
    exclude("androidx/compose/material/icons/sharp/**")

    // Unneeded Skiko drawing libraries
    // Note: This produces a platform-specific JAR based on the build environment
    val osName = System.getProperty("os.name").lowercase()
    val osArch = if (project.hasProperty("targetArch")) {
        (project.property("targetArch") as String).lowercase()
    } else {
        System.getProperty("os.arch").lowercase()
    }
    val isMac = osName.contains("mac")
    val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")
    println("#### DEBUG [shadowJar]: osName=$osName, osArch=$osArch, isMac=$isMac, isArm64=$isArm64")
    if (isMac && isArm64) exclude("libskiko-macos-x64.dylib")
    if (isMac && !isArm64) exclude("libskiko-macos-arm64.dylib")
}

// Custom task to unpack the OR-Tools native libraries
val unpackOrtNatives by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Unpacks OR-Tools native libraries to the bin/ortools-lib directory."

    val ortConfiguration = project.configurations.create("ortNatives") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies {
        // Define the native artifacts to download
        "ortNatives"(libs.ortools.native.macos.arm)
        "ortNatives"(libs.ortools.native.macos.x86)
        "ortNatives"(libs.ortools.native.linux.arm)
        "ortNatives"(libs.ortools.native.linux.x86)
        "ortNatives"(libs.ortools.native.win32.x86)
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

// Ensure the `build` task creates all artifacts for desktop packaging
tasks.named("build") {
    dependsOn(shadowJar)
    dependsOn(unpackOrtNatives)
}

// Configure the run task to use the unpacked native libraries
tasks.withType<JavaExec>().configureEach {
    dependsOn(unpackOrtNatives)

    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val isMac = osName.contains("mac")
    val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")
    val classifier = if (isMac && isArm64) "darwin-aarch64" else if (isMac) "darwin-x86-64" else if (osName.contains("win")) "win32-x86-64" else "linux-x86-64"

    systemProperty("java.library.path", "${project.rootDir}/bin/ortools-lib/ortools-$classifier")
}

// Log test output to console during build
tasks.withType<AbstractTestTask> {
    testLogging {
        // Choose which events you want to see
        events("passed", "skipped", "failed")

        // Include this if you also want to see println() output from your actual tests
        showStandardStreams = true
    }
}
