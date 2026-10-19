package io.archguard.gradle;

import io.archguard.api.Severity;
import io.archguard.engine.ModuleInput;
import io.archguard.gradle.internal.ArchGuardAnalysis;
import io.archguard.gradle.internal.ModuleDiscovery;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main gate: analyses every included subproject's compiled bytecode and
 * fails the build if the policy says so.
 *
 * <h2>Why {@code @UntrackedTask}</h2>
 *
 * The task inputs genuinely span the whole project tree (every subproject's
 * class dir + runtime classpath + policy file). Declaring every one of them as
 * a tracked input would require a wiring-heavy API and would still miss edge
 * cases like Kotlin's incremental output directories. Marking the task
 * untracked means Gradle re-runs it every time, which is the right behaviour
 * for a verification task of this kind — it's typically on the order of
 * seconds and the build already caches the upstream {@code classes} tasks.
 */
@UntrackedTask(because = "Analyses the full reactor; whole-reactor inputs are impractical to declare lazily, and the run is seconds, not minutes.")
public abstract class ArchGuardCheckTask extends DefaultTask {

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

    @Internal
    public abstract Property<Boolean> getSkip();

    public ArchGuardCheckTask() {
        setGroup("verification");
        setDescription("Analyse every subproject's compiled bytecode against the ArchGuard policy and fail on ERROR-level findings.");
    }

    @TaskAction
    public void run() throws Exception {
        if (Boolean.TRUE.equals(getSkip().getOrElse(false))) {
            getLogger().warn("ArchGuard skipped by request (archguard.skip = true).");
            return;
        }

        Set<String> excluded = new HashSet<>(getExcludedModules().getOrElse(List.of()));
        ModuleDiscovery.Discovery discovery = ModuleDiscovery.discover(getProject().getRootProject(), excluded);

        List<ModuleInput> modules = discovery.modules();
        if (modules.isEmpty()) {
            throw new GradleException(
                    "ArchGuard found no modules to analyse. Did you forget to run `./gradlew assemble` first,"
                            + " or exclude every subproject via archguard.excludedModules?");
        }

        List<String> diagnostics = new ArrayList<>(discovery.skippedProjects());
        if (Boolean.TRUE.equals(getAllowPartialReactor().getOrElse(false))) {
            diagnostics.add("archguard.allowPartialReactor = true; policy violations outside the"
                    + " selected scope would not be reported in this run");
        }

        Path output   = getOutputDirectory().get().getAsFile().toPath();
        Path config   = getConfigFile().get().getAsFile().toPath();
        Path baseline = asPathOrNull(getBaseline());
        Path supprs   = asPathOrNull(getSuppressions());
        Severity failOn = getFailOnSeverity().getOrNull();

        ArchGuardAnalysis.Inputs inputs = new ArchGuardAnalysis.Inputs(
                config, output,
                baseline, /* baselineOutput = */ null, /* overwriteBaseline = */ false,
                supprs,
                /* captureBaseline = */ false,
                /* enforce = */ true,
                failOn,
                modules,
                discovery.externalClasspath(),
                diagnostics
        );

        ArchGuardAnalysis.Outcome outcome = ArchGuardAnalysis.run(inputs);
        getLogger().lifecycle(outcome.consoleSummary());
        getLogger().lifecycle("ArchGuard reports: {}", output.toAbsolutePath());

        if (outcome.failed()) {
            throw new GradleException(
                    "ArchGuard: architecture policy violated. See " + output.resolve("index.html"));
        }
    }

    private static Path asPathOrNull(RegularFileProperty prop) {
        File f = prop.getAsFile().getOrNull();
        return f != null && f.exists() ? f.toPath() : null;
    }
}
