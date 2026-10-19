package io.archguard.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameworkIsolationRulesTest {

    private static final String GOOD = "io.archguard.archunit.fixtures.framework.good";
    private static final String BAD_JACKSON = "io.archguard.archunit.fixtures.framework.bad_jackson";

    private final JavaClasses good        = new ClassFileImporter().importPackages(GOOD);
    private final JavaClasses badJackson  = new ClassFileImporter().importPackages(BAD_JACKSON);

    @Test
    void noJackson_passes_on_clean_fixture() {
        FrameworkIsolationRules
                .noJacksonIn(GOOD + "..")
                .check(good);
    }

    @Test
    void noJackson_fires_when_jackson_leaks_in() {
        var rule = FrameworkIsolationRules.noJacksonIn(BAD_JACKSON + "..");

        assertThatThrownBy(() -> rule.check(badJackson))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jackson");
    }

    @Test
    void noSpring_passes_on_clean_fixture() {
        FrameworkIsolationRules.noSpringIn(GOOD + "..").check(good);
    }

    @Test
    void noJpa_passes_on_clean_fixture() {
        FrameworkIsolationRules.noJpaIn(GOOD + "..").check(good);
    }

    @Test
    void noLombok_passes_on_clean_fixture() {
        FrameworkIsolationRules.noLombokIn(GOOD + "..").check(good);
    }
}
