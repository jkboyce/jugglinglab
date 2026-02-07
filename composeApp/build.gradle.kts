//
// build.gradle.kts
//
// Juggling Lab build file for use with the Gradle build system.
// - `gradlew run` to build and run with Swing UI
// - `gradlew run -PJLcompose` to build and run with Compose UI
// - `gradlew build` to build bin/JugglingLab.jar
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.antlrKotlin)
}

object Versions {
    const val ORTOOLS_VERSION = "9.4.1874"
    const val KSOUP_VERSION = "0.2.5"
    const val ANTLR_KOTLIN_VERSION = "1.0.9"
    const val MATERIAL_ICONS_VERSION = "1.7.3"
}

// Custom task to generate ANTLR sources for siteswap parser
val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // ANTLR .g4 files are under composeApp/antlr
    source = fileTree(layout.projectDirectory.dir("antlr")) {
        include("**/*.g4")
    }

    // package name for generated source files
    val pkgName = "jugglinglab.notation.ssparser.generated"
    packageName = pkgName

    // we want visitors alongside listeners
    arguments = listOf("-visitor")

    // generated files are outputted in build/generatedAntlr/{package-name}
    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin {
                srcDir(generateKotlinGrammarSource)
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            //implementation(libs.androidx.lifecycle.viewmodelCompose)
            //implementation(libs.androidx.lifecycle.runtimeCompose)
            // Juggling Lab specific
            implementation("com.fleeksoft.ksoup:ksoup:${Versions.KSOUP_VERSION}")
            implementation("com.strumenta:antlr-kotlin-runtime:${Versions.ANTLR_KOTLIN_VERSION}")
            implementation("org.jetbrains.compose.material:material-icons-extended:${Versions.MATERIAL_ICONS_VERSION}")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
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

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Juggling Lab"
            packageVersion = "1.7.0"

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

    // Exclude several unneeded things from the JAR

    // OR-Tools binaries
    exclude("com/google/ortools/Loader.class")
    exclude("**/ortools-darwin*")
    exclude("**/ortools-win32*")
    exclude("**/ortools-linux*")
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

// Configure the run task to use the unpacked native libraries
tasks.withType<JavaExec>().configureEach {
    dependsOn(generateKotlinGrammarSource)
    dependsOn(unpackOrtNatives)

    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val isMac = osName.contains("mac")
    val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")
    val classifier = if (isMac && isArm64) "darwin-aarch64" else if (isMac) "darwin-x86-64" else if (osName.contains("win")) "win32-x86-64" else "linux-x86-64"

    systemProperty("java.library.path", "${project.rootDir}/bin/ortools-lib/ortools-$classifier")
}
