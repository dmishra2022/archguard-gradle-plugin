package com.example.adapter.rest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.archguard.archunit.CleanArchitectureRules;
import io.archguard.archunit.FrameworkIsolationRules;
import io.archguard.archunit.HexagonalArchitectureRules;
import org.junit.jupiter.api.Test;

/**
 * Gate 2 of the two-gate pattern. Runs during {@code ./gradlew test}, before
 * the Gradle-level {@code archguardCheck} task even starts.
 *
 * <p>Why have both? A quick summary:
 *
 * <ul>
 *   <li>These ArchUnit tests only need THIS module's classes on the classpath,
 *       so they fail-fast in a per-module CI shard.</li>
 *   <li>The Gradle {@code archguardCheck} task sees the whole reactor and can
 *       catch things that only make sense cross-module (DDD context cycles,
 *       whole-system framework signals, SARIF generation).</li>
 * </ul>
 *
 * <p>The two gates intentionally express the <em>same</em> intent in different
 * dialects. If one of them lets a change through, the other usually catches it.
 */
class ArchitectureTest {

    private final JavaClasses allExampleClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example");

    @Test
    void clean_architecture_layering() {
        // "Application" in Clean terms covers both the use-case
        // implementations and the port interfaces they own; both must be
        // listed because they live in sibling top-level packages in this
        // hex-flavoured layout.
        CleanArchitectureRules.layered()
                .withDomainPackage("..domain..")
                .withApplicationPackage("..application..", "..port..")
                .withInterfacePackage("..adapter..")
                .withInfrastructurePackage("..infrastructure..")
                .build()
                .check(allExampleClasses);
    }

    @Test
    void hexagonal_ports_are_interfaces() {
        HexagonalArchitectureRules
                .portsAreInterfaces("..port..")
                .check(allExampleClasses);
    }

    @Test
    void domain_is_framework_free() {
        CleanArchitectureRules
                .domainIsFrameworkFree("..domain..")
                .check(allExampleClasses);
    }

    @Test
    void no_jpa_in_the_core() {
        FrameworkIsolationRules
                .noJpaIn("..domain..", "..application..")
                .check(allExampleClasses);
    }

    @Test
    void no_jackson_in_the_core() {
        FrameworkIsolationRules
                .noJacksonIn("..domain..", "..application..")
                .check(allExampleClasses);
    }
}
