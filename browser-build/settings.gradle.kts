pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
 repositories { google(); mavenCentral() }
 versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "jugglinglab-browser-preview"
include(":composeApp")
project(":composeApp").projectDir = file("../composeApp")
project(":composeApp").buildFileName = "build.browser.gradle.kts"
