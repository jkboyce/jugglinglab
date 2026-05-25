plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}

// Custom lifecycle tasks to build specific targets
tasks.register("desktopBuild") {
    group = "custom builds"
    description = "Builds only the JVM Desktop artifacts"
    dependsOn(":composeApp:shadowJar", ":composeApp:jvmTest")
}

tasks.register("androidBuild") {
    group = "custom builds"
    description = "Builds only the Android APKs"
    dependsOn(":androidApp:assembleDebug")
}
