// The Gradle plugin itself.
//
// Published as:
//   plugin id:  io.archguard.gradle
//   maven coords: io.archguard.gradle:archguard-gradle-plugin:<version>
//
// Why Java (not Kotlin) for the plugin sources: the ArchGuard SDK is pure Java,
// and keeping this plugin Java-only means consumers do not pull in the Kotlin
// stdlib unnecessarily when they just want to run `./gradlew archguardCheck`.

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

description = "Gradle plugin wrapping the ArchGuard SDK: offline, evidence-based architecture checks over compiled bytecode."

dependencies {
    // Core SDK. api{} because the plugin's public task types expose Finding/
    // AnalysisResult shapes; downstream build-logic authors may want to touch them.
    api(rootProject.libs.archguard.api)
    api(rootProject.libs.archguard.core)

    // Jackson is already brought in transitively by archguard-core, but we
    // state it explicitly so a future SDK refactor that drops jackson from its
    // transitive closure does not silently break policy-file parsing.
    implementation(rootProject.libs.jackson.databind)

    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.assertj)
    // gradleTestKit() gives us GradleRunner for the functional tests that spin
    // up a real Gradle build in a tmp dir and prove the plugin works end-to-end.
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website.set("https://github.com/dmishra2022/archguard-gradle-plugin")
    vcsUrl.set("https://github.com/dmishra2022/archguard-gradle-plugin.git")
    plugins {
        create("archguard") {
            id          = "io.archguard.gradle"
            displayName = "ArchGuard"
            description = "Enforce architecture rules on compiled JVM bytecode."
            tags.set(listOf("architecture", "archguard", "clean-architecture", "hexagonal", "verification"))
            implementationClass = "io.archguard.gradle.ArchGuardPlugin"
        }
    }
}

// Reference the version catalog explicitly for subprojects since `libs` lives in the root.
val libs
    get() = rootProject.extensions.getByType(VersionCatalogsExtension::class).named("libs")

// -----------------------------------------------------------------------------
// Publishing — GitHub Packages
// -----------------------------------------------------------------------------
//
// Credentials come from env vars (CI) or ~/.gradle/gradle.properties (local).
// GITHUB_ACTOR and GITHUB_TOKEN are the names GitHub Actions sets automatically;
// locally you can create a PAT with `write:packages` scope and put it under
// gpr.user / gpr.key in your ~/.gradle/gradle.properties.
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url  = uri("https://maven.pkg.github.com/dmishra2022/archguard-gradle-plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}

// Separate source set for the GradleRunner-based functional tests.
// `register` is the non-deprecated replacement for the old `by creating` DSL
// (removed in Gradle 10, warned in 9).
val functionalTestSs = sourceSets.register("functionalTest").get()
gradlePlugin.testSourceSets(functionalTestSs)
configurations[functionalTestSs.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[functionalTestSs.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the GradleRunner-based functional tests for the ArchGuard plugin."
    group       = "verification"
    testClassesDirs = functionalTestSs.output.classesDirs
    classpath       = functionalTestSs.runtimeClasspath
    useJUnitPlatform()
    // Pass the archguard-sdk coordinates to the TestKit JVM so the generated
    // sample builds inside the tests can resolve io.archguard:archguard-core.
    systemProperty("archguard.sdk.version", libs.findVersion("archguard").get().requiredVersion)
}
tasks.named("check") { dependsOn(functionalTestTask) }
