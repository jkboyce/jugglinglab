// Web-only distribution of the upstream Juggling Lab app.
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
plugins {
 alias(libs.plugins.kotlinMultiplatform)
 alias(libs.plugins.composeMultiplatform)
 alias(libs.plugins.composeCompiler)
 alias(libs.plugins.antlrKotlin)
 alias(libs.plugins.kotlinSerialization)
}
group = "org.jugglinglab"
kotlin {
 @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
 wasmJs { browser(); nodejs(); binaries.executable() }
 sourceSets {
  commonMain { kotlin.srcDir(provider { tasks.named("generateKotlinGrammarSource").get() }) }
  commonMain.dependencies {
   implementation(libs.compose.runtime)
   implementation(libs.compose.foundation)
   implementation(libs.compose.material3)
   implementation(libs.compose.ui)
   implementation(libs.compose.components.resources)
   implementation(libs.compose.uiToolingPreview)
   implementation(libs.androidx.lifecycle.viewmodelCompose)
   implementation(libs.androidx.lifecycle.runtimeCompose)
   implementation(libs.fleeksoft.ksoup)
   implementation(libs.antlr.kotlin.runtime)
   implementation(libs.compose.material.icons.extended)
   api(libs.androidx.datastore.preferences.core)
   implementation(libs.jetbrains.navigation.compose)
   implementation(libs.okio)
  }
  commonTest.dependencies { implementation(libs.kotlin.test); implementation(libs.kotlinx.coroutines.test) }
  wasmJsMain.dependencies { implementation(libs.okio.fakefilesystem); implementation(libs.kotlinx.datetime) }
 }
}
tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
 source = fileTree(layout.projectDirectory.dir("antlr")) { include("**/*.g4") }
 packageName = "org.jugglinglab.notation.ssparser.generated"
 arguments = listOf("-visitor")
 outputDirectory = layout.buildDirectory.dir("generatedAntlr/org/jugglinglab/notation/ssparser/generated").get().asFile
}

// Same string-packaging step as the normal upstream build. The WASM engine
// prewarms these XML files so exceptions contain translated messages.
val copyStringsForPackaging = tasks.register<Copy>("copyStringsForPackaging") {
    from("src/commonMain/composeResources") {
        include("values/**/*.xml")
        include("values-*/**/*.xml")
    }
    into("src/commonMain/composeResources/files/strings")
}
tasks.matching {
    (it.name.contains("Resources") || it.name.contains("generateComposeResClass")) &&
        it.name != "copyStringsForPackaging"
}.configureEach { dependsOn(copyStringsForPackaging) }
