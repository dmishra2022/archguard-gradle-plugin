// Root build file.
//
// Why a root build.gradle.kts at all when both subprojects publish independently?
// Because every subproject needs the same JVM toolchain, the same source encoding,
// the same JUnit 5 wiring, and the same publishing-metadata boilerplate. Doing
// it once here keeps each subproject's build file small and focused on what is
// different about it.

plugins {
    base
}

// -----------------------------------------------------------------------------
// Convention block applied to every Java/Kotlin subproject
// -----------------------------------------------------------------------------
subprojects {
    apply(plugin = "java")

    // The `java` extension is only available after the java plugin is applied.
    extensions.configure<JavaPluginExtension> {
        toolchain {
            // Build with any current JDK >= 17; the toolchain picks one.
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        // Compile *to* 17 bytecode regardless of which JDK built it. Keeps
        // downstream consumers (which may be on Java 17 LTS) working.
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        // `-parameters` keeps method parameter names in the compiled bytecode;
        // Gradle needs them for its own introspection and ArchGuard uses them
        // when correlating findings back to source.
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }

    // Gradle 9 requires the JUnit Platform launcher on the test runtime
    // classpath explicitly. Done once here so every subproject that writes
    // JUnit 5 tests inherits it without having to repeat the ceremony.
    dependencies {
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("testRuntimeOnly", platform("org.junit:junit-bom:5.11.4"))
    }

    // Reproducible archives: identical inputs → byte-identical jar. Useful for
    // publishing because consumers can verify checksums.
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder  = true
    }
}
