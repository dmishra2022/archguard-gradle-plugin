package io.archguard.gradle;

import io.archguard.gradle.internal.ArchGuardAnalysis;
import io.archguard.gradle.internal.ModuleDiscovery;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Captures a baseline file recording every ERROR / WARN finding currently
 * present. Future runs ignore those exact findings (matched by fingerprint)
 * and only fail on <em>new</em> ones.
 *
 * <h2>When to use it</h2>
 *
 * Adopting ArchGuard on a brownfield codebase that already has violations:
 *
 * <ol>
 *   <li>Add the plugin, write {@code architecture.yml}.</li>
 *   <li>Run {@code ./gradlew archguardBaseline} once to capture the current
 *       debt.</li>
 *   <li>Commit {@code architecture-baseline.json}.</li>
 *   <li>Point {@code archguard.baseline} at it. From now on, the build only
 *       fails when a <em>new</em> violation appears — existing debt stays
 *       grandfathered.</li>
 *   <li>Pay the debt down over time by removing fingerprints from the
 *       baseline (or just deleting it when you reach zero).</li>
 * </ol>
 *
 * <h2>Why fingerprints and not line numbers</h2>
 *
 * The SDK computes a stable SHA-256 of {@code (ruleId, from, to, …)} per finding.
 * Re-formatting the code, moving a file, or renaming an unrelated class does
 * not invalidate the baseline. That is the whole point of the debt mechanism.
 */
@UntrackedTask(because = "Captures a baseline of the current reactor; always re-runs on request.")
public abstract class ArchGuardBaselineTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConfigFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @OutputFile
    public abstract RegularFileProperty getBaselineOutput();

    @Internal
    public abstract Property<Boolean> getOverwriteBaseline();

    @Internal
    public abstract ListProperty<String> getExcludedModules();

    public ArchGuardBaselineTask() {
        setGroup("verification");
        setDescription("Snapshot current ERROR/WARN findings into a baseline file so only NEW violations fail the build going forward.");
    }

    @TaskAction
    public void run() throws Exception {
        Set<String> excluded = new HashSet<>(getExcludedModules().getOrElse(List.of()));
        ModuleDiscovery.Discovery discovery = ModuleDiscovery.discover(getProject().getRootProject(), excluded);

        Path output = getOutputDirectory().get().getAsFile().toPath();
        Path baselineOut = getBaselineOutput().get().getAsFile().toPath();

        ArchGuardAnalysis.Inputs inputs = new ArchGuardAnalysis.Inputs(
                getConfigFile().get().getAsFile().toPath(),
                output,
                /* baseline       = */ null,
                /* baselineOutput = */ baselineOut,
                Boolean.TRUE.equals(getOverwriteBaseline().getOrElse(false)),
                /* suppressions   = */ null,
                /* captureBaseline= */ true,
                /* enforce        = */ false,
                /* failOnOverride = */ null,
                discovery.modules(),
                discovery.externalClasspath(),
                discovery.skippedProjects()
        );

        ArchGuardAnalysis.Outcome outcome = ArchGuardAnalysis.run(inputs);
        getLogger().lifecycle(outcome.consoleSummary());
        getLogger().lifecycle("ArchGuard baseline written: {}", baselineOut.toAbsolutePath());
    }
}
