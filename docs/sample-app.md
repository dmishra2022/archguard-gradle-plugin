# `sample-app/` — three-module composite build

A tiny three-module Gradle application that proves **both** gates work on realistic code. It lives in its own composite build (`sample-app/settings.gradle.kts` does `includeBuild("..")`) so a fresh clone of the repo can run both gates end-to-end without first publishing anything.

```bash
# Need JDK 17–25 (not 27). See README.md "Compatibility".
export JAVA_HOME=/path/to/jdk-25
cd sample-app
../gradlew check
```

Expected output includes:

```
> Task :archguardCheck
ArchGuard 0.1.0: 7 classes, 67 dependencies, 0 findings

> Task :adapter-rest:test
ArchitectureTest > no_jpa_in_the_core()              PASSED
ArchitectureTest > clean_architecture_layering()     PASSED
ArchitectureTest > domain_is_framework_free()        PASSED
ArchitectureTest > hexagonal_ports_are_interfaces()  PASSED
ArchitectureTest > no_jackson_in_the_core()          PASSED

BUILD SUCCESSFUL
```

---

## Layout

```
sample-app/
├── settings.gradle.kts         includeBuild("..") so the plugin + rules library
│                               are resolved from the sibling source tree
├── build.gradle.kts            applies io.archguard.gradle; adds archunit-rules
│                               as testImplementation to every subproject
├── architecture.yml            policy consumed by archguardCheck
├── domain/                     DOMAIN role
│   └── src/main/java/com/example/domain/
│       ├── OrderId.java
│       └── Order.java
├── application/                APPLICATION role + ports as top-level sibling
│   └── src/main/java/com/example/
│       ├── application/PlaceOrderService.java
│       └── port/
│           ├── in/PlaceOrderUseCase.java    INBOUND_PORT role
│           └── out/OrderRepository.java     OUTBOUND_PORT role
└── adapter-rest/               INBOUND_ADAPTER role; also hosts the ArchUnit test
    └── src/
        ├── main/java/com/example/adapter/rest/OrderController.java
        └── test/java/com/example/adapter/rest/ArchitectureTest.java
```

---

## Why ports live under `com.example.port.*`, not `com.example.application.port.*`

ArchGuard assigns roles to classes by package glob. If both `com.example.application..` (APPLICATION) and `com.example.application.port..` (OUTBOUND_PORT) match a class, the engine throws:

```
Ambiguous role mapping for com.example.application.port.out.OrderRepository: [APPLICATION, OUTBOUND_PORT]
```

ArchGuard's own `/examples/clean/` ships ports as a top-level sibling of application precisely to sidestep this. The sample mirrors that convention.

---

## The two gates in this sample

### Gate 1 — `archguardCheck` (whole-reactor, bytecode)

Reads `architecture.yml`, analyses all three modules' bytecode together, checks:

- The hexagonal built-in rules (`HEX-001`..`HEX-004`): no DOMAIN → APPLICATION, no APPLICATION → concrete adapter, no port depending on an infrastructure type, no inbound-adapter ↔ outbound-adapter coupling.
- `DDD-001` and `DDD-002`: cross-context access to internal types + bounded-context cycles.
- `MODEL-001`: `requireClassification: true` is on, so any class under `com.example..` that is not covered by a role fails. Forces developers to classify new code intentionally.

### Gate 2 — `ArchitectureTest.java` (per-module, ArchUnit)

Lives in the `:adapter-rest` test source set because that module transitively compiles against everything else and so can see the whole code graph via `ClassFileImporter`. The test asserts:

- `clean_architecture_layering` — Clean dependency rule (uses varargs to tell the "Application" layer about both `..application..` and `..port..`).
- `hexagonal_ports_are_interfaces` — the OrderPort / OrderRepository must stay interfaces.
- `domain_is_framework_free` — nothing framework-y in the domain.
- `no_jpa_in_the_core` — JPA stays in the persistence adapter (when it exists).
- `no_jackson_in_the_core` — Jackson stays in the HTTP adapter (when it exists).

Both gates say roughly the same thing in different dialects. In practice:

- **Gate 2 fails first** during local development because it runs inside `test`, before `check`. The stack trace points at a specific method.
- **Gate 1 catches cross-module issues** Gate 2 can't see — e.g. a dependency cycle between the `:orders` and `:billing` contexts.
- Gate 1 also **produces SARIF**, which GitHub Code Scanning can ingest to annotate PRs inline.

Delete either gate and the project still works; keep both and you have defence in depth.

---

## Changing the sample to deliberately break it

Try any of these one-line edits and re-run `../gradlew check`:

1. **Break the Hex dependency rule.** Add `import com.example.application.PlaceOrderService;` into `domain/Order.java` and reference `PlaceOrderService.class` somewhere. You'll see `archguardCheck` fail with `HEX-001: Domain depends on an outer layer or framework`.
2. **Break framework isolation.** Add `com.fasterxml.jackson:jackson-databind` to the domain module and `@JsonProperty` on an Order field. The `no_jackson_in_the_core` ArchUnit test fails first.
3. **Add an unclassified class.** Create `sample-app/application/src/main/java/com/example/util/Something.java`. `MODEL-001` (`requireClassification: true`) fails.
4. **Make a port concrete.** Convert `PlaceOrderUseCase` from an interface to a class. The `hexagonal_ports_are_interfaces` ArchUnit test fails.

Each failure is produced by the gate best positioned to catch it, which is the point.
