# `archunit-rules` — Composable ArchUnit rule factories

A tiny library of ready-made [ArchUnit](https://www.archunit.org/) rules that enforce the usual architecture styles. Depend on it from your test module and write per-module architecture tests that run inside `./gradlew test`.

```kotlin
dependencies {
    testImplementation("io.archguard.gradle:archunit-rules:0.1.0-SNAPSHOT")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
```

Every rule factory returns a stock `com.tngtech.archunit.lang.ArchRule`, so you can `.check(JavaClasses)`, compose with your own rules, or use them anywhere ArchUnit's DSL expects a rule.

---

## Package: `io.archguard.archunit`

### `CleanArchitectureRules`

Enforces Uncle Bob's Clean Architecture layering.

| Factory | What it enforces |
| :--- | :--- |
| `CleanArchitectureRules.layered()` | Fluent builder for a 4-layer `(Domain, Application, Interface, Infrastructure)` architecture. Dependencies must point inwards. |
| `CleanArchitectureRules.domainIsFrameworkFree(domainPackage)` | Domain classes must not depend on Spring, Jakarta/Javax EE, Hibernate/JPA, Jackson, Gson, SLF4J, or `java.util.logging`. |

```java
class CleanArchitectureTest {
    private final JavaClasses all = new ClassFileImporter().importPackages("com.example");

    @Test void layering() {
        CleanArchitectureRules.layered()
            .withDomainPackage("..domain..")
            .withApplicationPackage("..application..", "..port..")   // varargs — ports live beside application
            .withInterfacePackage("..adapter..")
            .withInfrastructurePackage("..infrastructure..")
            .build()
            .check(all);
    }

    @Test void domainStaysPure() {
        CleanArchitectureRules.domainIsFrameworkFree("..domain..").check(all);
    }
}
```

Why varargs on every `with*Package`? Real codebases rarely have one glob per layer — e.g. ports live as a top-level sibling of application in the ArchGuard-recommended layout and must be included under "Application".

The layered rule uses `withOptionalLayers(true)` so a brand-new codebase (no infrastructure classes yet) passes rather than failing with "layer empty".

### `HexagonalArchitectureRules`

Enforces Alistair Cockburn's Hexagonal (Ports & Adapters) architecture.

| Factory | What it enforces |
| :--- | :--- |
| `HexagonalArchitectureRules.hex()...build()` | Fluent builder for a `(Domain, Application, Adapter)` layering with ports as a cross-cutting concern. |
| `HexagonalArchitectureRules.portsAreInterfaces(portPackage)` | Classes in the port package must be interfaces. A concrete class named `SomethingPort` is a common accident that silently converts a supposedly-swappable port into a leaky abstraction. |
| `HexagonalArchitectureRules.adaptersUseOnlyPorts(adapter, app, port)` | Adapters depending on application internals (anything in `app` that isn't under `port`) is forbidden. |

```java
HexagonalArchitectureRules.hex()
    .withApplicationPackage("..application..")
    .withDomainPackage("..domain..")
    .withAdapterPackage("..adapter..")
    .withPortPackage("..port..")
    .build()
    .check(all);
```

### `ScreamingArchitectureRules`

Top-level package names should **scream** what the application does, not what framework it uses.

| Factory | What it enforces |
| :--- | :--- |
| `ScreamingArchitectureRules.topLevelPackagesMustBeOneOf(basePackage, allowedNames...)` | Positive rule: every class under `basePackage` must live under a sub-package named after one of your allowed business concepts. |
| `ScreamingArchitectureRules.topLevelPackagesMustNotMatchTechnicalNames(basePackage)` | Negative rule: `controller`, `service`, `repository`, `dao`, `model`, `entity`, `dto`, `util`, `helper`, `common`, `manager` and their plurals are banned as top-level packages. Useful on brownfield projects where enumerating every business concept is impractical. |

```java
ScreamingArchitectureRules
    .topLevelPackagesMustBeOneOf("com.acme", "order", "shipping", "billing")
    .check(all);
```

The negative rule uses `allowEmptyShould(true)` — a codebase with zero technical top-level packages is the happy case, not a reason to fail the build.

### `FrameworkIsolationRules`

Stop frameworks from leaking across layer boundaries. Orthogonal to the layer rules: layer rules say "domain cannot see application"; these say "domain cannot see Spring".

| Factory | Blocked packages |
| :--- | :--- |
| `FrameworkIsolationRules.noSpringIn(packages...)` | `org.springframework..` |
| `FrameworkIsolationRules.noJacksonIn(packages...)` | `com.fasterxml.jackson..` |
| `FrameworkIsolationRules.noJpaIn(packages...)` | `jakarta.persistence..`, `javax.persistence..`, `org.hibernate..` |
| `FrameworkIsolationRules.noLombokIn(packages...)` | `lombok..` |

Each returns an `ArchRule` so you can mix them freely:

```java
@Test void coreIsFrameworkFree() {
    FrameworkIsolationRules.noSpringIn("..domain..", "..application..").check(all);
    FrameworkIsolationRules.noJpaIn   ("..domain..", "..application..").check(all);
    FrameworkIsolationRules.noJacksonIn("..domain..", "..application..").check(all);
}
```

---

## Writing your own rules alongside

`archunit-rules` is just a thin layer on top of ArchUnit. Everything you can do with raw ArchUnit still works. Compose:

```java
ArchRule myCombinedRule = CleanArchitectureRules.layered()
        .withDomainPackage("..domain..").build()
        .and(FrameworkIsolationRules.noSpringIn("..domain.."))
        .and(/* your own rule */ );
```

---

## Self-tests

Every factory has a self-test under `archunit-rules/src/test/java/io/archguard/archunit/` that imports deliberately-violating fixtures and asserts the rule fires. The intent is to catch silent regressions: if you upgrade ArchUnit or refactor a rule and it no longer detects the thing it is supposed to detect, the self-test turns red.
