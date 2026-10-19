# archguard-gradle-plugin

Enforce architecture rules on **Gradle** JVM projects. Two complementary gates:

| Gate | Where it runs | What it sees | What it writes |
| :--- | :--- | :--- | :--- |
| **`io.archguard.gradle` plugin** | `./gradlew check` (and friends) | The whole reactor's compiled bytecode | `build/reports/archguard/{archguard.json, archguard.sarif, index.html}` |
| **`archunit-rules` library** | Your module's unit tests (`./gradlew test`) | Just that module's classes | Standard JUnit test report |

Both gates read the **same intent** — Clean / Hexagonal / Screaming / Framework-isolated architecture — in two different dialects, so one usually catches what the other misses. You can adopt either in isolation.

Everything here wraps the [ArchGuard SDK](https://github.com/archguard/archguard) (`io.archguard:archguard-{api,core}`), the same engine the official Maven plugin uses, so a team running both tools gets byte-identical findings for the same `architecture.yml` on the same bytecode.

---

## Status

- Repository: `dmishra2022/archguard-gradle-plugin`
- Published to **GitHub Packages Maven** (`https://maven.pkg.github.com/dmishra2022/archguard-gradle-plugin`).
- Version: `0.1.0-SNAPSHOT` (SDK `0.1.0`).
- License: MIT.

## Compatibility

| Thing | Version |
| :--- | :--- |
| JVM to run the build | **JDK 17–25**. JDK 27 is **not** currently supported — the underlying ArchGuard 0.1.0 SDK fails on JDK 27 with `Unresolved direct application dependency: X -> java.lang.Object`. This is a known upstream issue, not a plugin bug. |
| JVM bytecode target of your code | 17+ (toolchain-based) |
| Gradle | 8.6+ (tested on 9.8.1) |
| Groovy / Kotlin DSL | Both work; examples below use Kotlin DSL |

---

## Install

### 1. Tell Gradle where to find the plugin + the archunit-rules library

In **`settings.gradle.kts`**:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/dmishra2022/archguard-gradle-plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/dmishra2022/archguard-gradle-plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
```

> GitHub Packages Maven requires authentication **even for public artifacts**. Set the credentials as GitHub env vars in CI, or put them in `~/.gradle/gradle.properties` locally as `gpr.user` / `gpr.key` (PAT with `read:packages`).

### 2. Apply the plugin at the **root** project

In **root `build.gradle.kts`**:

```kotlin
plugins {
    id("io.archguard.gradle") version "0.1.0-SNAPSHOT"
}

archguard {
    // Defaults shown; override as needed. See docs/gradle-plugin.md.
    // configFile.set(layout.projectDirectory.file("architecture.yml"))
    // outputDirectory.set(layout.buildDirectory.dir("reports/archguard"))
}
```

> **Why the root project only?** ArchGuard needs the full reactor to make reliable statements about the dependency rule, exactly like the ArchGuard Maven plugin's `check-reactor` goal. Applying the plugin to a subproject throws `IllegalStateException`.

### 3. Write `architecture.yml` at the project root

Minimal:

```yaml
schemaVersion: 1
roots: ["com.yourcompany.."]
profiles: [hexagonal, ddd]
roles:
  DOMAIN:          ["com.yourcompany.domain.."]
  APPLICATION:     ["com.yourcompany.application.."]
  OUTBOUND_PORT:   ["com.yourcompany.port.out.."]
  INBOUND_PORT:    ["com.yourcompany.port.in.."]
  INBOUND_ADAPTER: ["com.yourcompany.adapter.."]
contexts:
  - id: default
    packages: ["com.yourcompany.."]
    exposedPackages: ["com.yourcompany.port.."]
requireClassification: true
failOn: ERROR
newOnly: false
```

See the ArchGuard upstream docs for the full schema; this project does not modify it.

### 4. (Optional) Add `archunit-rules` to a subproject that writes architecture tests

```kotlin
dependencies {
    testImplementation("io.archguard.gradle:archunit-rules:0.1.0-SNAPSHOT")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
```

Write a test:

```java
class ArchitectureTest {
    private final JavaClasses all = new ClassFileImporter().importPackages("com.yourcompany");

    @Test void layering() {
        CleanArchitectureRules.layered()
            .withDomainPackage("..domain..")
            .withApplicationPackage("..application..", "..port..")
            .withInterfacePackage("..adapter..")
            .withInfrastructurePackage("..infrastructure..")
            .build()
            .check(all);
    }
}
```

See [docs/archunit-rules.md](docs/archunit-rules.md) for every rule factory with a usage example.

---

## Tasks the plugin registers

| Task | Behaviour | When to use it |
| :--- | :--- | :--- |
| `archguardCheck` | Analyse → **fail** on policy violations. Wired into the `check` lifecycle. | Normal CI gate. |
| `archguardReport` | Analyse → write reports, never fail. | CI jobs that upload SARIF to GitHub Code Scanning without gating. |
| `archguardBaseline` | Snapshot current ERROR/WARN findings so future runs only fail on **new** ones. | One-shot, when adopting on a brownfield codebase. |

All three write their reports to `build/reports/archguard/`.

---

## The two-gate pattern — why have both?

The Gradle plugin and the ArchUnit library intentionally overlap but do not duplicate work.

| | Gradle plugin (`archguardCheck`) | ArchUnit rules in `test` |
| :--- | :--- | :--- |
| Scope | Whole reactor at once | One module |
| When it fires | At `check` time | At `test` time, before `check` |
| How a developer gets the signal | `BUILD FAILED` + an HTML report | A failing JUnit test with a stack trace to the exact method |
| What it uniquely catches | DDD context cycles, cross-module policy violations, framework-signal cross-references | Package-naming hygiene, framework-import leaks, Clean/Hex invariants per module |
| What it writes | JSON + SARIF + HTML (SARIF is machine-readable by GitHub Code Scanning) | Standard JUnit XML |

In practice: the ArchUnit test fails sooner during local development and gives a precise stack. The plugin finds the harder cross-module issues and generates the artefacts CI tooling cares about.

---

## Repository layout

```
archguard-gradle-plugin/
├── plugin/                     The Gradle plugin itself (io.archguard.gradle)
├── archunit-rules/             The shared ArchUnit rule library
├── sample-app/                 Composite build that applies BOTH gates
├── docs/
│   ├── gradle-plugin.md
│   ├── archunit-rules.md
│   └── sample-app.md
└── .github/workflows/          CI (test on PR) + publish (on tag)
```

The `sample-app/` is a composite build that `includeBuild("..")` back into this repo, so a fresh clone can run `cd sample-app && ../gradlew check` end-to-end without publishing anything.

---

## Build from source

```bash
# Needs JDK 17–25 (not 27).
export JAVA_HOME=/path/to/jdk-25
./gradlew build publishToMavenLocal

cd sample-app
../gradlew check              # runs both gates against the sample app
```

See [docs/sample-app.md](docs/sample-app.md) for a walk-through of what the sample proves.

---

## Links

- [Gradle plugin reference →](docs/gradle-plugin.md)
- [ArchUnit rules reference →](docs/archunit-rules.md)
- [Sample app walk-through →](docs/sample-app.md)
- [ArchGuard SDK upstream](https://github.com/archguard/archguard)
- [ArchUnit upstream](https://www.archunit.org/)
