package io.archguard.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreamingArchitectureRulesTest {

    private static final String BASE_GOOD = "io.archguard.archunit.fixtures.screaming.good";
    private static final String BASE_BAD  = "io.archguard.archunit.fixtures.screaming.bad";

    private final JavaClasses good = new ClassFileImporter().importPackages(BASE_GOOD);
    private final JavaClasses bad  = new ClassFileImporter().importPackages(BASE_BAD);

    @Test
    void topLevelAllowList_passes_when_only_business_names_are_used() {
        ScreamingArchitectureRules
                .topLevelPackagesMustBeOneOf(BASE_GOOD, "order", "billing")
                .check(good);
    }

    @Test
    void topLevelAllowList_fires_on_technical_name() {
        var rule = ScreamingArchitectureRules
                .topLevelPackagesMustBeOneOf(BASE_BAD, "order", "billing");

        assertThatThrownBy(() -> rule.check(bad))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("controller");
    }

    @Test
    void technicalBlacklist_passes_when_no_technical_top_level() {
        ScreamingArchitectureRules
                .topLevelPackagesMustNotMatchTechnicalNames(BASE_GOOD)
                .check(good);
    }

    @Test
    void technicalBlacklist_fires_on_controller_and_service() {
        var rule = ScreamingArchitectureRules
                .topLevelPackagesMustNotMatchTechnicalNames(BASE_BAD);

        assertThatThrownBy(() -> rule.check(bad))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("technical top-level package");
    }
}
