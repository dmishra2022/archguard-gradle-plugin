package io.archguard.archunit;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Ready-made ArchUnit rules enforcing Alistair Cockburn's Hexagonal
 * Architecture (aka Ports &amp; Adapters).
 *
 * <h2>What Hexagonal says</h2>
 *
 * There is a central <strong>application core</strong>. Everything else is an
 * <strong>adapter</strong>. Adapters talk to the core only through
 * <strong>ports</strong> — interfaces owned by the core — never through
 * concrete classes.
 *
 * <pre>
 *   driving adapters       → ┐
 *   (REST, CLI, …)           │
 *                            │       ┌─────────────────┐
 *                            └──────►│                 │
 *                                    │   application   │
 *   driven adapters        ┌────────►│      core       │
 *   (DB, external APIs, …) │         │                 │
 *                          │         └─────────────────┘
 *                       (ports)
 * </pre>
 *
 * <h2>Why Hexagonal and not just Clean</h2>
 *
 * Both enforce "dependencies point inwards". Hexagonal is a stricter shape:
 * everything on the outside has to go through a port interface. If your team
 * already works that way, these rules codify it; if not, {@link
 * CleanArchitectureRules} is the gentler onboarding.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * class HexArchTest {
 *     private final JavaClasses all = new ClassFileImporter().importPackages("com.example");
 *
 *     @Test void portsAndAdapters() {
 *         HexagonalArchitectureRules.hex()
 *             .withApplicationPackage("..application..")
 *             .withDomainPackage("..domain..")
 *             .withAdapterPackage("..adapter..")
 *             .withPortPackage("..application.port..")
 *             .build()
 *             .check(all);
 *     }
 * }
 * }</pre>
 */
public final class HexagonalArchitectureRules {

    private HexagonalArchitectureRules() {}

    public static Builder hex() {
        return new Builder();
    }

    /**
     * Port interfaces must <em>be</em> interfaces. A concrete class named
     * {@code SomethingPort} is a common accident that silently converts a
     * supposedly-swappable port into a leaky abstraction.
     */
    public static ArchRule portsAreInterfaces(String portPackage) {
        return classes()
                .that().resideInAPackage(portPackage)
                .should().beInterfaces()
                .because("Hexagonal ports are interfaces; a concrete port is a leaky abstraction.");
    }

    /**
     * Adapters must not reach into application internals that live outside
     * {@code portPackage}. In other words: the only application-side things an
     * adapter sees are the ports.
     */
    public static ArchRule adaptersUseOnlyPorts(String adapterPackage,
                                                String applicationPackage,
                                                String portPackage) {
        return noClasses()
                .that().resideInAPackage(adapterPackage)
                .should().dependOnClassesThat().resideInAPackage(applicationPackage)
                .andShould().dependOnClassesThat().resideOutsideOfPackage(portPackage)
                .because("Hexagonal: adapters depend on the core only via port interfaces, not on concrete core classes.");
    }

    public static final class Builder {
        private String application = "..application..";
        private String domain      = "..domain..";
        private String adapter     = "..adapter..";
        private String port        = "..application.port..";

        private Builder() {}

        public Builder withApplicationPackage(String p) { this.application = p; return this; }
        public Builder withDomainPackage(String p)      { this.domain      = p; return this; }
        public Builder withAdapterPackage(String p)     { this.adapter     = p; return this; }
        public Builder withPortPackage(String p)        { this.port        = p; return this; }

        public ArchRule build() {
            return Architectures.layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain").definedBy(domain)
                    .layer("Application").definedBy(application)
                    .layer("Adapter").definedBy(adapter)

                    .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter")

                    .as("Hexagonal (Ports & Adapters): Adapter -> Application -> Domain; nothing crosses backwards.");
        }
    }
}
