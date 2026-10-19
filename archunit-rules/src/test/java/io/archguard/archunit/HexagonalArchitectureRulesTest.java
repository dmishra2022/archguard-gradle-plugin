package io.archguard.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-test for Hexagonal rule factories. Mirrors {@link
 * CleanArchitectureRulesTest} in style: a passing fixture, a failing fixture,
 * and a message-assertion so a silent regression would surface.
 */
class HexagonalArchitectureRulesTest {

    private static final String FIXTURES_ROOT = "io.archguard.archunit.fixtures.hex";

    private final JavaClasses good = new ClassFileImporter()
            .importPackages(FIXTURES_ROOT + ".good");

    private final JavaClasses badConcretePort = new ClassFileImporter()
            .importPackages(FIXTURES_ROOT + ".bad_concrete_port");

    @Test
    void hex_passes_on_good_fixture() {
        HexagonalArchitectureRules.hex()
                .withApplicationPackage(FIXTURES_ROOT + ".good.application..")
                .withDomainPackage(FIXTURES_ROOT + ".good.domain..")
                .withAdapterPackage(FIXTURES_ROOT + ".good.adapter..")
                .withPortPackage(FIXTURES_ROOT + ".good.application.port..")
                .build()
                .check(good);
    }

    @Test
    void portsAreInterfaces_passes_when_port_is_interface() {
        HexagonalArchitectureRules
                .portsAreInterfaces(FIXTURES_ROOT + ".good.application.port..")
                .check(good);
    }

    @Test
    void portsAreInterfaces_fires_when_port_is_concrete() {
        var rule = HexagonalArchitectureRules
                .portsAreInterfaces(FIXTURES_ROOT + ".bad_concrete_port.application.port..");

        assertThatThrownBy(() -> rule.check(badConcretePort))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("interface");
    }
}
