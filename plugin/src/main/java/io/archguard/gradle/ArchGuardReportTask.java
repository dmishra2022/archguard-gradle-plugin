package io.archguard.gradle;

import io.archguard.api.Severity;
import io.archguard.engine.ModuleInput;
import io.archguard.gradle.internal.ArchGuardAnalysis;
import io.archguard.gradle.internal.ModuleDiscovery;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Same analysis as {@link ArchGuardCheckTask} but <strong>never</strong> fails
 * the build. Writes the JSON / SARIF / HTML reports and logs the console
 * summary. Useful for:
 *
 * <ul>
 *   <li>CI jobs that upload the SARIF report to GitHub Code Scanning without
 *       gating the pipeline on it.</li>
 *   <li>Local exploration — "show me where the dependencies currently point"
 *       without the build going red.</li>
 *   <li>Pre-adoption: generating the first report to decide the baseline from.</li>
 * </ul>
 */
@UntrackedTask(because = "Analyses the full reactor; see ArchGuardCheckTask for rationale.")
public abstract class ArchGuardReportTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConfigFile();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getBaseline();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSuppressions();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Internal
    public abstract Property<Severity> getFailOnSeverity();

    @Internal
    public abstract ListProperty<String> getExcludedModules();

    @Internal
    public abstract Property<Boolean> getAllowPartialReactor();

    public ArchGuardReportTask() {
        setGroup("verification");
        setDescription("Analyse bytecode and write ArchGuard reports; does NOT fail the build on violations.");
    }

    @TaskAction
    public void run() throws Exception {
        Set<String> excluded = new HashSet<>(getExcludedModules().getOrElse(List.of()));
        ModuleDiscovery.Discovery discovery = ModuleDiscovery.discover(getProject().getRootProject(), excluded);

        Path output   = getOutputDirectory().get().getAsFile().toPath();
        Path config   = getConfigFile().get().getAsFile().toPath();
        Path baseline = asPathOrNull(getBaseline());
        Path supprs   = asPathOrNull(getSuppressions());

        ArchGuardAnalysis.Inputs inputs = new ArchGuardAnalysis.Inputs(
                config, output,
                baseline, /* baselineOutput = */ null, false,
                supprs,
                /* captureBaseline = */ false,
                /* enforce = */ false,
                getFailOnSeverity().getOrNull(),
                discovery.modules(),
                discovery.externalClasspath(),
                discovery.skippedProjects()
        );

        ArchGuardAnalysis.Outcome outcome = ArchGuardAnalysis.run(inputs);
        getLogger().lifecycle(outcome.consoleSummary());
        getLogger().lifecycle("ArchGuard reports (no enforcement): {}", output.toAbsolutePath());
    }

    private static Path asPathOrNull(RegularFileProperty prop) {
        File f = prop.getAsFile().getOrNull();
        return f != null && f.exists() ? f.toPath() : null;
    }
}
