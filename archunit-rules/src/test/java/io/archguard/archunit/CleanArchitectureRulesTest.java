package io.archguard.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-test for the Clean Architecture rule factories.
 *
 * <p>Every rule is exercised twice: once over a "good" fixture tree that
 * respects the layering, once over a deliberately-broken "bad" fixture tree.
 * If the rule failed to fire on the bad tree, this test would still pass —
 * which is why the bad case uses {@code assertThatThrownBy} + a message
 * assertion. A silent regression would be caught.
 */
class CleanArchitectureRulesTest {

    private static final String FIXTURES_ROOT = "io.archguard.archunit.fixtures.clean";

    private final JavaClasses good = new ClassFileImporter()
            .importPackages(FIXTURES_ROOT + ".good");

    private final JavaClasses badLayering = new ClassFileImporter()
            .importPackages(FIXTURES_ROOT + ".bad_layering");

    private final JavaClasses badFramework = new ClassFileImporter()
            .importPackages(FIXTURES_ROOT + ".bad_framework");

    @Test
    void layering_passes_on_good_fixture() {
        CleanArchitectureRules.layered()
                .withDomainPackage(FIXTURES_ROOT + ".good.domain..")
                .withApplicationPackage(FIXTURES_ROOT + ".good.application..")
                .withInterfacePackage(FIXTURES_ROOT + ".good.interfaces..")
                .withInfrastructurePackage(FIXTURES_ROOT + ".good.infrastructure..")
                .build()
                .check(good);
    }

    @Test
    void layering_fires_on_bad_fixture() {
        var rule = CleanArchitectureRules.layered()
                .withDomainPackage(FIXTURES_ROOT + ".bad_layering.domain..")
                .withApplicationPackage(FIXTURES_ROOT + ".bad_layering.application..")
                .withInterfacePackage(FIXTURES_ROOT + ".bad_layering.interfaces..")
                .withInfrastructurePackage(FIXTURES_ROOT + ".bad_layering.infrastructure..")
                .build();

        assertThatThrownBy(() -> rule.check(badLayering))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Infrastructure");
    }

    @Test
    void domainIsFrameworkFree_passes_on_good_fixture() {
        CleanArchitectureRules
                .domainIsFrameworkFree(FIXTURES_ROOT + ".good.domain..")
                .check(good);
    }

    @Test
    void domainIsFrameworkFree_fires_on_slf4j_leak() {
        var rule = CleanArchitectureRules
                .domainIsFrameworkFree(FIXTURES_ROOT + ".bad_framework.domain..");

        assertThatThrownBy(() -> rule.check(badFramework))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.slf4j");
    }

    @Test
    void builder_defaults_do_not_blow_up() {
        // Not a semantic test — just guards against NPEs in the builder shape.
        assertThat(CleanArchitectureRules.layered().build()).isNotNull();
    }
}
