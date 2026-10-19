package io.archguard.gradle.internal;

import io.archguard.api.AnalysisResult;
import io.archguard.api.ArchitectureRule;
import io.archguard.api.Severity;
import io.archguard.config.Policies;
import io.archguard.config.Policy;
import io.archguard.engine.ArchitectureEngine;
import io.archguard.engine.Debt;
import io.archguard.engine.ModuleInput;
import io.archguard.report.Reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Pure-Java entry point into the ArchGuard SDK, isolated from any Gradle API
 * so it can be unit-tested with plain JUnit.
 *
 * <p>Mirrors the control flow of {@code io.archguard.maven.AbstractArchitectureMojo}
 * so a team running both the Maven plugin and this Gradle plugin gets
 * byte-identical findings for the same {@code architecture.yml} on the same
 * bytecode.
 */
public final class ArchGuardAnalysis {

    /** Result of a run — mirrors the subset of SDK state Gradle consumers care about. */
    public record Outcome(AnalysisResult result, Policy policy, boolean failed, String consoleSummary) {
    }

    public record Inputs(
            Path configFile,
            Path outputDirectory,
            Path baseline,
            Path baselineOutput,
            boolean overwriteBaseline,
            Path suppressions,
            boolean captureBaseline,
            boolean enforce,
            Severity failOnOverride,
            List<ModuleInput> modules,
            List<Path> externalClasspath,
            List<String> extraDiagnostics
    ) {
    }

    private ArchGuardAnalysis() {}

    public static Outcome run(Inputs in) throws IOException {
        Objects.requireNonNull(in, "inputs");
        Objects.requireNonNull(in.configFile(), "configFile");
        Objects.requireNonNull(in.outputDirectory(), "outputDirectory");

        Policy policy = Policies.read(in.configFile());

        // SPI: load any extra rules a build has put on the plugin classpath.
        // ArchGuard uses the same mechanism in its Maven plugin, so custom
        // rule jars authored for Maven work as-is here.
        List<ArchitectureRule> extensions = new ArrayList<>();
        ServiceLoader.load(ArchitectureRule.class, ArchGuardAnalysis.class.getClassLoader())
                .forEach(extensions::add);

        AnalysisResult result = new ArchitectureEngine()
                .analyze(in.modules(), in.externalClasspath(), policy, extensions);

        if (!in.extraDiagnostics().isEmpty()) {
            List<String> merged = new ArrayList<>(result.diagnostics());
            merged.addAll(in.extraDiagnostics());
            result = new AnalysisResult(
                    result.schemaVersion(),
                    result.engineVersion(),
                    result.policyHash(),
                    result.analyzedClasses(),
                    result.dependencies(),
                    result.findings(),
                    merged
            );
        }

        // Baseline mode: write a snapshot of the current ERROR/WARN findings
        // and return without enforcement so the build does not fail during
        // debt capture.
        if (in.captureBaseline()) {
            Path out = Objects.requireNonNull(in.baselineOutput(), "baselineOutput");
            if (Files.exists(out) && !in.overwriteBaseline()) {
                throw new IOException(
                        "Baseline already exists at " + out
                                + "; review the diff and re-run with archguard.overwriteBaseline = true");
            }
            Debt.writeBaseline(out, result);
            Reports.write(in.outputDirectory(), result);
            return new Outcome(result, policy, false, Reports.console(result));
        }

        // Normal mode: apply any existing baseline + suppressions, then write
        // the three canonical report files.
        result = Debt.apply(result, in.baseline(), in.suppressions(), Clock.systemUTC());
        Reports.write(in.outputDirectory(), result);

        Severity failOn = in.failOnOverride() != null ? in.failOnOverride() : policy.failOn();
        boolean fails = in.enforce() && result.fails(failOn, policy.newOnly());

        return new Outcome(result, policy, fails, Reports.console(result));
    }
}
