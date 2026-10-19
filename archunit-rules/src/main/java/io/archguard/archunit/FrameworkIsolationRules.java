package io.archguard.archunit;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Rules that stop frameworks from leaking across layer boundaries.
 *
 * <h2>Why framework isolation is a separate concern</h2>
 *
 * Even a project with perfect {@link CleanArchitectureRules Clean} or {@link
 * HexagonalArchitectureRules Hexagonal} layering can accidentally leak Spring
 * annotations into the domain, pull Jackson {@code @JsonProperty} into
 * use-case code, or let Hibernate {@code @Entity} escape its data-adapter.
 * Layer rules say "domain cannot see application"; these say "domain cannot
 * see Spring". They are orthogonal.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * class FrameworkIsolationTest {
 *     private final JavaClasses all = new ClassFileImporter().importPackages("com.example");
 *
 *     @Test void noSpringInDomain() {
 *         FrameworkIsolationRules.noSpringIn("..domain..").check(all);
 *     }
 *
 *     @Test void noJacksonOutsideRestAdapter() {
 *         FrameworkIsolationRules.noJacksonIn("..domain..", "..application..").check(all);
 *     }
 *
 *     @Test void noJpaIn() {
 *         FrameworkIsolationRules.noJpaIn("..domain..", "..application..").check(all);
 *     }
 * }
 * }</pre>
 */
public final class FrameworkIsolationRules {

    private FrameworkIsolationRules() {}

    /**
     * Classes in {@code packages} must not depend on any class in
     * {@code org.springframework..} — framework code, context, beans, and all
     * the stereotype annotations ({@code @Component}, {@code @Service},
     * {@code @Autowired}, …).
     */
    public static ArchRule noSpringIn(String... packages) {
        return noClasses()
                .that().resideInAnyPackage(packages)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..")
                .because("Keep Spring out of these layers so they can be tested without a Spring context.");
    }

    /**
     * Classes in {@code packages} must not depend on Jackson databind or
     * annotations ({@code @JsonProperty}, {@code @JsonCreator}, …). Jackson
     * belongs in the HTTP adapter, not in use-case code or the domain.
     */
    public static ArchRule noJacksonIn(String... packages) {
        return noClasses()
                .that().resideInAnyPackage(packages)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fasterxml.jackson..")
                .because("Serialization is an adapter concern; keeping Jackson out of these layers means a transport change does not ripple into the core.");
    }

    /**
     * Classes in {@code packages} must not depend on Jakarta Persistence /
     * Hibernate ({@code jakarta.persistence..}, {@code javax.persistence..},
     * {@code org.hibernate..}). JPA leaking into the domain is the single
     * biggest source of "the domain model is the database model" accidents.
     */
    public static ArchRule noJpaIn(String... packages) {
        return noClasses()
                .that().resideInAnyPackage(packages)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "org.hibernate..")
                .because("Persistence is an adapter concern; keeping JPA out of these layers prevents the domain model from being warped by the database schema.");
    }

    /**
     * Classes in {@code packages} must not depend on Lombok. Lombok at the
     * domain boundary makes equality, hash-codes and constructor generation
     * invisible — all three are usually things a domain author wants to make
     * explicit. (Also runs into module-path and record-interop issues in
     * modern codebases.)
     */
    public static ArchRule noLombokIn(String... packages) {
        return noClasses()
                .that().resideInAnyPackage(packages)
                .should().dependOnClassesThat().resideInAnyPackage("lombok..")
                .because("Hidden generated code (@Data, @Builder, @EqualsAndHashCode) obscures intent in these layers; prefer records or explicit code.");
    }
}
