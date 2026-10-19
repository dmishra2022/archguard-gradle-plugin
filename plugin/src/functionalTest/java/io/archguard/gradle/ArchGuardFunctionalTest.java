package io.archguard.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end functional test. Generates a tiny multi-project Gradle build in
 * a temporary directory, applies this plugin via {@code withPluginClasspath()}
 * so GradleRunner picks it up from the Gradle TestKit classpath, and runs the
 * three ArchGuard tasks against the generated project.
 *
 * <h2>What we are proving</h2>
 *
 * <ol>
 *   <li>Applying the plugin and running {@code archguardReport} on a clean
 *       project succeeds and writes the canonical report files.</li>
 *   <li>Running {@code archguardCheck} against a well-layered project passes.</li>
 *   <li>Deliberately introducing a layering violation makes
 *       {@code archguardCheck} fail with a non-zero exit and the HTML report
 *       is still produced.</li>
 * </ol>
 *
 * <h2>Why the ArchGuard SDK coordinates are passed through</h2>
 *
 * The TestKit JVM uses an isolated Gradle instance that doesn't see this
 * project's buildscript classpath. So the generated project needs to resolve
 * {@code io.archguard:archguard-core:X.Y.Z} itself. We give it
 * {@code mavenLocal()} + the version read from this project's version catalog
 * via the {@code archguard.sdk.version} system property set in
 * {@code plugin/build.gradle.kts}.
 */
class ArchGuardFunctionalTest {

    @TempDir Path projectDir;

    private String sdkVersion() {
        String v = System.getProperty("archguard.sdk.version");
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                    "archguard.sdk.version system property not set; the functionalTest task should pass it in");
        }
        return v;
    }

    private void writeFile(String relativePath, String contents) throws IOException {
        Path f = projectDir.resolve(relativePath);
        Files.createDirectories(f.getParent());
        Files.writeString(f, contents);
    }

    /**
     * Writes a tiny single-module project with two <em>packages</em>
     * ({@code com.example.domain} and {@code com.example.app}). Keeping both
     * packages in a single Gradle module means I can switch between a legal
     * dependency flow and an illegal one by editing one file — no module
     * dependency cycles to work around.
     */
    private void writeGoodProject() throws IOException {
        writeFile("settings.gradle.kts", """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenLocal(); mavenCentral() }
            }
            dependencyResolutionManagement {
                repositories { mavenLocal(); mavenCentral() }
            }
            rootProject.name = "sample"
            include(":app")
            """);

        writeFile("build.gradle.kts", """
            plugins { id("io.archguard.gradle") }
            subprojects { apply(plugin = "java") }
            archguard {
                configFile.set(layout.projectDirectory.file("architecture.yml"))
            }
            """);

        writeFile("architecture.yml", """
            schemaVersion: 1
            roots: ["com.example.."]
            # Activates the HEX-* built-in rules (HEX-001 catches
            # DOMAIN -> APPLICATION flow, which is what the "bad" case violates).
            profiles: [hexagonal]
            roles:
              DOMAIN:      ["com.example.domain.."]
              APPLICATION: ["com.example.app.."]
            contexts:
              - id: sample
                packages: ["com.example.."]
                exposedPackages: ["com.example.app.."]
            requireClassification: true
            failOn: ERROR
            newOnly: false
            """);

        writeFile("app/build.gradle.kts", "// java inherited from subprojects {}\n");
        writeFile("app/src/main/java/com/example/domain/Order.java", """
            package com.example.domain;
            public final class Order {
                private final String id;
                public Order(String id) { this.id = id; }
                public String id() { return id; }
            }
            """);
        writeFile("app/src/main/java/com/example/app/PlaceOrder.java", """
            package com.example.app;
            import com.example.domain.Order;
            public final class PlaceOrder {
                public Order run(String id) { return new Order(id); }
            }
            """);
    }

    /**
     * Edits the good project so a DOMAIN-role class reaches up into an
     * APPLICATION-role class — the dependency rule violation ArchGuard is
     * built to catch. Because both packages live in the same Gradle module,
     * the Java compiler is happy; only ArchGuard objects.
     */
    private void breakDependencyRule() throws IOException {
        // Keep the one-arg constructor so PlaceOrder.java still compiles. The
        // violation is the import + method reference reaching from DOMAIN into
        // APPLICATION.
        writeFile("app/src/main/java/com/example/domain/Order.java", """
            package com.example.domain;
            import com.example.app.PlaceOrder; // forbidden by HEX-001
            public final class Order {
                private final String id;
                public Order(String id) { this.id = id; }
                public String id() { return id; }
                public String fire(PlaceOrder useCase) { return useCase.run(id).id(); }
            }
            """);
    }

    private GradleRunner runner(String... args) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(args)
                .forwardOutput();
    }

    @Test
    void archguardReport_runs_and_writes_report_files() throws IOException {
        writeGoodProject();

        BuildResult result = runner("archguardReport", "--stacktrace").build();

        assertThat(result.task(":archguardReport").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        Path reports = projectDir.resolve("build/reports/archguard");
        assertThat(reports.resolve("archguard.json")).exists();
        assertThat(reports.resolve("archguard.sarif")).exists();
        assertThat(reports.resolve("index.html")).exists();
    }

    @Test
    void archguardCheck_passes_on_well_layered_project() throws IOException {
        writeGoodProject();

        BuildResult result = runner("archguardCheck", "--stacktrace").build();

        assertThat(result.task(":archguardCheck").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void archguardCheck_fails_when_dependency_rule_is_violated() throws IOException {
        writeGoodProject();
        breakDependencyRule();

        BuildResult result = runner("archguardCheck", "--stacktrace").buildAndFail();

        assertThat(result.task(":archguardCheck").getOutcome())
                .isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput())
                .contains("ArchGuard: architecture policy violated");

        assertThat(projectDir.resolve("build/reports/archguard/index.html")).exists();
    }
}
