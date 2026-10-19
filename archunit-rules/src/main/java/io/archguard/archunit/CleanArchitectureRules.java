package io.archguard.archunit;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Ready-made ArchUnit rules enforcing Uncle Bob's Clean Architecture layering.
 *
 * <h2>What Clean Architecture says</h2>
 *
 * The dependency rule: source code dependencies may only point <em>inwards</em>.
 * Nothing in an inner layer knows anything about an outer layer.
 *
 * <pre>
 *   infrastructure  -+                 (frameworks, drivers, databases, UI)
 *                    v
 *   interface       -+                 (adapters: controllers, presenters, gateways)
 *                    v
 *   application     -+                 (use-case orchestration)
 *                    v
 *   domain                             (entities + business rules)
 * </pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * class CleanArchitectureTest {
 *     private final JavaClasses all = new ClassFileImporter().importPackages("com.example");
 *
 *     @Test void layeringIsCorrect() {
 *         CleanArchitectureRules.layered()
 *             .withDomainPackage("..domain..")
 *             .withApplicationPackage("..application..")
 *             .withInterfacePackage("..interfaces..")
 *             .withInfrastructurePackage("..infrastructure..")
 *             .build()
 *             .check(all);
 *     }
 *
 *     @Test void domainStaysPure() {
 *         CleanArchitectureRules.domainIsFrameworkFree("..domain..").check(all);
 *     }
 * }
 * }</pre>
 */
public final class CleanArchitectureRules {

    private CleanArchitectureRules() {}

    /** Starts a Clean-Architecture layer definition. */
    public static Builder layered() {
        return new Builder();
    }

    /**
     * The domain layer must not depend on any framework package. The default
     * blacklist covers Spring, Jakarta/Javax EE, Hibernate/JPA, Jackson, Gson,
     * Slf4j and java.util.logging — the usual suspects.
     *
     * <p>The point of a pure domain is that you can run <em>all</em> business
     * logic tests without a Spring context, without a database, and without
     * any annotation-processor magic. This rule enforces it.
     */
    public static ArchRule domainIsFrameworkFree(String domainPackage) {
        return noClasses()
                .that().resideInAnyPackage(domainPackage)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "javax..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "com.google.gson..",
                        "org.slf4j..",
                        "java.util.logging..")
                .because("Clean Architecture: the domain layer is framework-free so business rules remain testable in isolation.");
    }

    /**
     * Builder for the layered Clean-Architecture rule.
     *
     * <p>Every {@code with*Package} setter takes <em>varargs</em> because real
     * codebases are rarely one glob per layer. The Application layer, for
     * example, might legitimately be
     * {@code ("..application..", "..port..")} if ports live as a top-level
     * sibling of application (which is the ArchGuard-recommended layout — the
     * pattern {@code "..application.."} alone would miss them).
     */
    public static final class Builder {
        private String[] domain         = { "..domain.." };
        private String[] application    = { "..application.." };
        private String[] interfaces     = { "..interfaces.." };
        private String[] infrastructure = { "..infrastructure.." };

        private Builder() {}

        public Builder withDomainPackage(String... p)         { this.domain         = p; return this; }
        public Builder withApplicationPackage(String... p)    { this.application    = p; return this; }
        public Builder withInterfacePackage(String... p)      { this.interfaces     = p; return this; }
        public Builder withInfrastructurePackage(String... p) { this.infrastructure = p; return this; }

        public ArchRule build() {
            return Architectures.layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain").definedBy(domain)
                    .layer("Application").definedBy(application)
                    .layer("Interface").definedBy(interfaces)
                    .layer("Infrastructure").definedBy(infrastructure)

                    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Interface").mayOnlyBeAccessedByLayers("Infrastructure")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Interface", "Infrastructure")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Interface", "Infrastructure")

                    // Allow a brand-new codebase (no infrastructure classes yet)
                    // to pass this rule — "the layer isn't populated" is not a
                    // violation.
                    .withOptionalLayers(true)

                    .as("Clean Architecture layering: dependencies point inwards (Domain <- Application <- Interface <- Infrastructure)");
        }
    }
}
