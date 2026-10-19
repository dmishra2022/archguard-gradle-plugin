pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Allow subproject build scripts to add repositories if they ever need to.
    repositoriesMode = RepositoriesMode.PREFER_PROJECT
    repositories {
        // Order matters: mavenLocal first so we can resolve the ArchGuard SDK
        // (0.1.0 is `mvn install`-ed locally; it's not yet on Maven Central at
        // the time of writing — see docs/gradle-plugin.md for the roadmap).
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "archguard-gradle-plugin"

// Two published artifacts:
//   - archguard-gradle-plugin: the Gradle plugin that wraps the ArchGuard SDK
//   - archunit-rules:          a shared library of ready-made ArchUnit rules
// The sample-app lives in its own composite build (sample-app/settings.gradle.kts) so
// it exercises the plugin exactly like any downstream consumer would.
include(":plugin")
include(":archunit-rules")

project(":plugin").projectDir            = file("plugin")
project(":archunit-rules").projectDir    = file("archunit-rules")
