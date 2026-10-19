# `io.archguard.gradle` — Gradle plugin reference

## What it does

Wraps the [ArchGuard SDK](https://github.com/archguard/archguard) (`io.archguard:archguard-{api,core}`) in a Gradle plugin that:

1. Collects every subproject's compiled `main` bytecode plus its runtime classpath.
2. Reads a YAML policy (`architecture.yml`) describing roles, contexts and profiles.
3. Runs the SDK's `ArchitectureEngine.analyze(...)` once over the whole reactor.
4. Writes three canonical reports: `archguard.json`, `archguard.sarif`, `index.html`.
5. Fails the build if any finding exceeds the policy's `failOn` severity.

Design goal: the Gradle plugin and the official ArchGuard Maven plugin produce **byte-identical findings** for the same policy over the same bytecode. All real analysis lives in the SDK; this plugin only translates the Gradle project tree into `(modules, classpath)` the SDK can consume.

---

## Application point

Apply at the **root** project:

```kotlin
plugins {
    id("io.archguard.gradle") version "0.1.0-SNAPSHOT"
}
```

Applying to a subproject throws `IllegalStateException` with a message pointing you to the right level. The reasoning is the same as the Maven plugin's `check-reactor` goal — ArchGuard cannot reliably reason about the dependency rule without seeing every classified class.

Applying the plugin also implicitly applies the Gradle `base` plugin at the root, so a `check` task always exists for `archguardCheck` to be wired into.

---

## DSL

```kotlin
archguard {
    configFile.set(layout.projectDirectory.file("architecture.yml"))
    outputDirectory.set(layout.buildDirectory.dir("reports/archguard"))
    failOnSeverity.set(io.archguard.api.Severity.ERROR)       // overrides policy.failOn if set
    baseline.set(layout.projectDirectory.file("architecture-baseline.json"))
    baselineOutput.set(layout.projectDirectory.file("architecture-baseline.json"))
    overwriteBaseline.set(false)
    suppressions.set(layout.projectDirectory.file("architecture-suppressions.json"))
    excludedModules.addAll(":integration-tests", ":benchmarks")
    allowPartialReactor.set(false)
    skip.set(false)
}
```

### Every property, in detail

| Property | Default | Why it exists |
| :--- | :--- | :--- |
| `configFile` | `rootProject/architecture.yml` | The YAML policy. Shared with the ArchGuard Maven plugin so a team using both tools has a single source of truth. |
| `outputDirectory` | `build/reports/archguard` | Where `archguard.json`, `archguard.sarif` and `index.html` are written. SARIF is readable by GitHub Code Scanning out of the box. |
| `failOnSeverity` | *(policy decides)* | Override the policy's `failOn`. Rarely needed. |
| `baseline` | *(unset)* | Enables "debt" mode: findings whose fingerprints are already on the baseline do not fail the build. New findings do. |
| `baselineOutput` | `rootProject/architecture-baseline.json` | Where `archguardBaseline` writes its snapshot. |
| `overwriteBaseline` | `false` | By default, writing on top of an existing baseline is an error (prevents accidental regressions). Flip to true when you deliberately regenerate. |
| `suppressions` | *(unset)* | Explicit, auditable `(fingerprint, owner, reason, expires)` entries; preferred over baselines for permanent waivers. |
| `excludedModules` | `[]` | Gradle project paths (e.g. `:integration-tests`) whose bytecode should NOT contribute to the analysis. Equivalent to the Maven plugin's `excludedModules`. |
| `allowPartialReactor` | `false` | ArchGuard's safety rail. By default, running the check task without every subproject in the task graph is an error — a partial scan can mask dependency-rule violations. Set to true only when you have deliberately scoped the build. |
| `skip` | `false` | Kill switch, equivalent to `-Darchguard.skip=true` in the Maven plugin. Primarily for emergency breakage recovery. |

---

## Tasks

### `archguardCheck`

- **Group**: `verification`
- **Lifecycle**: hooked into `check`, so `./gradlew build` runs it.
- **Behaviour**:
  1. For every subproject that applies the `java` plugin: pick its `classes/java/main` (fallback: first non-empty entry of `SourceSet.main.output.classesDirs`).
  2. Pass the (modules, external-classpath) pair to the SDK.
  3. If `baseline` is set, apply it (plus `suppressions`) via `Debt.apply(...)` so grandfathered findings do not fail the build.
  4. Write the three canonical reports.
  5. Fail with a `GradleException` if any remaining finding meets or exceeds `failOnSeverity`.

Marked `@UntrackedTask` because the real inputs are the whole reactor's class dirs + runtime classpath + policy file, which are impractical to declare lazily. The analysis itself is seconds, not minutes, so re-running every time is the right trade-off.

### `archguardReport`

Same analysis, same reports, **but never fails**. Useful for:

- CI jobs that upload SARIF to GitHub Code Scanning without gating the pipeline.
- Local exploration — "show me where the dependencies currently point" without the build going red.
- Pre-adoption: generating the first report to decide the baseline from.

### `archguardBaseline`

Captures a baseline file recording every ERROR/WARN finding currently present. Future runs ignore those exact findings (matched by a stable SHA-256 fingerprint) and only fail on **new** ones.

The debt workflow in full:

1. Add the plugin, write `architecture.yml`.
2. `./gradlew archguardBaseline` once to capture current debt.
3. Commit `architecture-baseline.json`.
4. Point `archguard.baseline` at it. From now on, the build only fails on **new** violations.
5. Pay the debt down over time by removing fingerprints from the baseline (or just deleting the file when you hit zero).

Fingerprints are a stable SHA-256 of `(ruleId, from, to, …)`. Re-formatting the code, moving a file, or renaming an unrelated class does **not** invalidate the baseline. That is the whole point of the debt mechanism.

---

## Module discovery

The plugin's `ModuleDiscovery` walks `rootProject.getAllprojects()` and for each project:

1. If it applies the `java` plugin, pick its `main` source set.
2. Prefer `build/classes/java/main`; fall back to `SourceSet.main.output.classesDirs` first entry (covers Kotlin-only modules).
3. Classify the project under its Gradle path (e.g. `:order-adapter-rest`).
4. Collect its `runtimeClasspath` configuration; subtract the module class dirs we already know about; the remainder is the **external classpath** passed to the SDK.

Non-Java subprojects (pure platforms, documentation modules, Android, …) are silently skipped. Unresolvable configurations (constraints-only, variants without a target, etc.) are also skipped — the analysis simply gets a smaller classpath and ArchGuard will report any resulting `UnresolvedType` diagnostics itself.

---

## SPI: custom rules

The plugin loads any Java `ServiceLoader`-registered `io.archguard.api.ArchitectureRule` on the plugin classpath. Add your rule jar as a `buildscript` dependency alongside the plugin — the SDK uses the same mechanism in its Maven plugin, so custom rule jars authored for Maven work as-is here.

---

## Known limitations and compatibility notes

- **JDK 27 is not supported.** The ArchGuard 0.1.0 SDK's class resolver fails on JDK 27 with `Unresolved direct application dependency: X -> java.lang.Object. Supply its bytecode on the analysis classpath.` This is reproducible on upstream `examples/clean/` with the Maven plugin, so it is not a Gradle-plugin bug. Run the Gradle daemon on JDK 17–25 until the SDK ships a fix.
- The SDK does **not** analyse reflection, dynamic DI, or runtime-stitched profiles. This is a static-bytecode tool.
- The plugin must be applied at the root project. Composite builds should apply it in each included build's root as needed.
- The plugin currently resolves the SDK from `mavenLocal()` by default (SDK 0.1.0 is `mvn install`-ed locally; it is not yet on Maven Central at the time of writing). Change the version catalog entry once the SDK publishes there.
