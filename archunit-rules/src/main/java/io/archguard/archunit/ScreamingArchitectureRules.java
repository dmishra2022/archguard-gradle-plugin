package io.archguard.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Enforces "Screaming Architecture": the top-level package names should
 * <em>scream</em> what the application does, not what framework it uses.
 *
 * <h2>What Screaming Architecture says</h2>
 *
 * Open a project. The top-level packages should be things like
 * {@code order}, {@code shipping}, {@code billing}. They should not be
 * {@code controller}, {@code service}, {@code repository}, {@code model}.
 * The architecture tells you what the system is <em>about</em>, not what
 * mechanics it was built with.
 *
 * <p>This matters for a surprising reason: new team members orient faster on
 * a screaming codebase. A folder named {@code order} immediately maps to the
 * business; a folder named {@code service} tells you nothing.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * class ScreamingTest {
 *     private final JavaClasses all = new ClassFileImporter().importPackages("com.example");
 *
 *     @Test void topLevelPackagesAreBusinessConcepts() {
 *         ScreamingArchitectureRules.topLevelPackagesMustBeOneOf(
 *                 "com.example",
 *                 "order", "shipping", "billing"
 *         ).check(all);
 *     }
 *
 *     @Test void technicalNamesAreBanned() {
 *         ScreamingArchitectureRules.topLevelPackagesMustNotMatchTechnicalNames(
 *                 "com.example"
 *         ).check(all);
 *     }
 * }
 * }</pre>
 */
public final class ScreamingArchitectureRules {

    /**
     * Common names that indicate <em>how</em> a class works rather than
     * <em>what</em> it does. Having one of these at the top level is a smell.
     */
    public static final List<String> TECHNICAL_PACKAGE_NAMES = List.of(
            "controller", "controllers",
            "service",    "services",
            "repository", "repositories",
            "dao",
            "model",      "models",
            "entity",     "entities",
            "dto",        "dtos",
            "util",       "utils",
            "helper",     "helpers",
            "common",
            "manager",    "managers"
    );

    private ScreamingArchitectureRules() {}

    /**
     * Every class under {@code basePackage} must live in a sub-package whose
     * first segment is one of {@code allowedBusinessConcepts}.
     *
     * <p>Example: with {@code basePackage="com.acme"} and allowed names
     * {@code "order", "billing", "shipping"}, a class
     * {@code com.acme.order.OrderService} passes but
     * {@code com.acme.controller.OrderController} fails.
     */
    public static ArchRule topLevelPackagesMustBeOneOf(String basePackage, String... allowedBusinessConcepts) {
        List<String> allowed = List.of(allowedBusinessConcepts);
        return classes()
                .that().resideInAPackage(basePackage + "..")
                .should(new ArchCondition<JavaClass>("live in a package named after a business concept: " + allowed) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String pkg = item.getPackageName();
                        if (!pkg.startsWith(basePackage)) return;
                        String rest = pkg.substring(basePackage.length());
                        if (rest.isEmpty() || rest.equals(".")) return;
                        String first = rest.startsWith(".") ? rest.substring(1) : rest;
                        int dot = first.indexOf('.');
                        if (dot >= 0) first = first.substring(0, dot);
                        if (!allowed.contains(first)) {
                            events.add(SimpleConditionEvent.violated(item,
                                    "class " + item.getName()
                                            + " lives under non-business top-level package '" + first + "'"));
                        }
                    }
                })
                .because("Screaming Architecture: top-level packages name the business, not the mechanics.");
    }

    /**
     * The inverse rule: regardless of what business names you allow, these
     * well-known technical names must <em>never</em> appear as a top-level
     * package under {@code basePackage}.
     *
     * <p>Useful on brownfield projects where enumerating every business
     * concept is impractical but banning the known-bad names is still a win.
     */
    public static ArchRule topLevelPackagesMustNotMatchTechnicalNames(String basePackage) {
        DescribedPredicate<JavaClass> isTechnicalTop = new DescribedPredicate<>("has a technical top-level package") {
            @Override
            public boolean test(JavaClass c) {
                String pkg = c.getPackageName();
                if (!pkg.startsWith(basePackage)) return false;
                String rest = pkg.substring(basePackage.length());
                if (rest.isEmpty() || rest.equals(".")) return false;
                String first = rest.startsWith(".") ? rest.substring(1) : rest;
                int dot = first.indexOf('.');
                if (dot >= 0) first = first.substring(0, dot);
                return TECHNICAL_PACKAGE_NAMES.contains(first.toLowerCase());
            }
        };
        return classes()
                .that(isTechnicalTop)
                .should(new ArchCondition<JavaClass>("be moved under a business-named package") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        events.add(SimpleConditionEvent.violated(item,
                                "class " + item.getName()
                                        + " lives under a technical top-level package (controller/service/dao/model/etc.)"));
                    }
                })
                // A codebase with zero technical top-level packages is the
                // happy case, not a reason to fail the build.
                .allowEmptyShould(true)
                .because("Screaming Architecture: avoid top-level packages that describe mechanics instead of the business.");
    }
}
