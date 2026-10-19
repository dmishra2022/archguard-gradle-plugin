// Sample app lives in its own composite build so it exercises the plugin
// EXACTLY the way a downstream consumer would:
//   - plugin resolved through the plugin management repository
//   - archunit-rules resolved through the dependency repository
//   - no magic `project(":plugin")` shortcuts
//
// `includeBuild("..")` tells Gradle to substitute `io.archguard.gradle:*`
// coordinates with artifacts built from the sibling project, so a fresh clone
// of the root repo can run `cd sample-app && ../gradlew check` without first
// having to publish anything to mavenLocal.
pluginManagement {
    includeBuild("..")
    repositories {
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    includeBuild("..") {
        // Substitute the Maven coord of archunit-rules with the module in the
        // sibling build.
        dependencySubstitution {
            substitute(module("io.archguard.gradle:archunit-rules"))
                .using(project(":archunit-rules"))
        }
    }
    repositoriesMode = RepositoriesMode.PREFER_PROJECT
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "sample-app"
include(":domain", ":application", ":adapter-rest")
