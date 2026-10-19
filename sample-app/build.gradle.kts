// Sample app showing BOTH ArchGuard gates working together:
//
//   Gate 1: Gradle `check` runs `archguardCheck`, which analyses the compiled
//           bytecode of every subproject against architecture.yml.
//
//   Gate 2: Each subproject's unit tests include an ArchitectureTest.java that
//           uses the archunit-rules library to assert Clean / Hex /
//           Framework-Isolation invariants.
//
// Both gates are intentional. They catch different things:
//
//   - archguardCheck sees across modules, inspects the SDK's rich model
//     (contexts, DDD cycles, framework classification signals), and ships
//     SARIF for GitHub Code Scanning.
//
//   - ArchUnit tests run inside the test task, so they fail per-module without
//     needing other modules compiled, and they catch package-level issues
//     (naming, framework imports) that the policy YAML does not model.

plugins {
    id("io.archguard.gradle")
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        // archunit-rules on test classpath. Resolved via composite build
        // substitution declared in settings.gradle.kts.
        add("testImplementation", "io.archguard.gradle:archunit-rules:0.1.0-SNAPSHOT")
        add("testImplementation", "com.tngtech.archunit:archunit-junit5:1.4.1")
        add("testImplementation", platform("org.junit:junit-bom:5.11.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testImplementation", "org.assertj:assertj-core:3.27.3")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
}

archguard {
    // Default location is architecture.yml at the project root, so this line
    // is here only to document it, not because it's needed.
    configFile.set(layout.projectDirectory.file("architecture.yml"))
}
