package io.archguard.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.TaskProvider;

/**
 * Plugin entry point. Apply with:
 *
 * <pre>{@code
 * plugins { id("io.archguard.gradle") version "<version>" }
 * }</pre>
 *
 * <h2>What applying the plugin does</h2>
 *
 * <ol>
 *   <li>Registers the {@code archguard { }} DSL extension (see {@link ArchGuardExtension}).</li>
 *   <li>Registers three tasks:
 *     <ul>
 *       <li>{@code archguardCheck}   — analyse + fail-on-violation. Wired into {@code check}.</li>
 *       <li>{@code archguardReport}  — analyse + write reports but <em>never</em> fail. Useful for CI uploads.</li>
 *       <li>{@code archguardBaseline} — capture a debt baseline (one-shot, intentionally not wired into any lifecycle).</li>
 *     </ul>
 *   </li>
 *   <li>Wires {@code archguardCheck} into the standard {@code check} lifecycle
 *       so {@code ./gradlew build} runs it as part of the usual verification pass.</li>
 *   <li>Declares that every subproject that applies the {@code java} plugin must
 *       have its {@code classes} task done before {@code archguardCheck} runs.
 *       Without this, {@code ./gradlew archguardCheck} starting from a clean
 *       state would find empty class directories and fail with a confusing
 *       "no modules" error.</li>
 * </ol>
 *
 * <h2>Where this plugin can be applied</h2>
 *
 * On the <strong>root</strong> project of a Gradle build. Applying it to a
 * subproject is not supported — ArchGuard needs the whole reactor to make
 * reliable statements about the dependency rule, exactly the same reason the
 * Maven plugin's {@code check-reactor} goal must run at the aggregator.
 */
public final class ArchGuardPlugin implements Plugin<Project> {

    public static final String CHECK_TASK_NAME    = "archguardCheck";
    public static final String REPORT_TASK_NAME   = "archguardReport";
    public static final String BASELINE_TASK_NAME = "archguardBaseline";

    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new IllegalStateException(
                    "The ArchGuard plugin must be applied to the root project. Applied to '"
                            + project.getPath() + "' instead.");
        }

        ArchGuardExtension ext = project.getExtensions().create(ArchGuardExtension.NAME, ArchGuardExtension.class);

        // Conventional defaults so a bare `plugins { id("io.archguard.gradle") }`
        // works without any configuration provided the project ships an
        // architecture.yml at its root.
        ext.getConfigFile().convention(
                project.getLayout().getProjectDirectory().file("architecture.yml"));
        ext.getOutputDirectory().convention(
                project.getLayout().getBuildDirectory().dir("reports/archguard"));
        ext.getBaselineOutput().convention(
                project.getLayout().getProjectDirectory().file("architecture-baseline.json"));

        TaskProvider<ArchGuardCheckTask> check = project.getTasks().register(
                CHECK_TASK_NAME, ArchGuardCheckTask.class, t -> wireCommon(t, ext));
        check.configure(t -> {
            t.getBaseline().convention(ext.getBaseline());
            t.getSuppressions().convention(ext.getSuppressions());
        });

        TaskProvider<ArchGuardReportTask> report = project.getTasks().register(
                REPORT_TASK_NAME, ArchGuardReportTask.class, t -> {
                    t.getConfigFile().convention(ext.getConfigFile());
                    t.getOutputDirectory().convention(ext.getOutputDirectory());
                    t.getBaseline().convention(ext.getBaseline());
                    t.getSuppressions().convention(ext.getSuppressions());
                    t.getFailOnSeverity().convention(ext.getFailOnSeverity());
                    t.getExcludedModules().convention(ext.getExcludedModules());
                    t.getAllowPartialReactor().convention(ext.getAllowPartialReactor());
                });

        project.getTasks().register(BASELINE_TASK_NAME, ArchGuardBaselineTask.class, t -> {
            t.getConfigFile().convention(ext.getConfigFile());
            t.getOutputDirectory().convention(ext.getOutputDirectory());
            t.getBaselineOutput().convention(ext.getBaselineOutput());
            t.getOverwriteBaseline().convention(ext.getOverwriteBaseline());
            t.getExcludedModules().convention(ext.getExcludedModules());
        });

        // The `base` plugin guarantees a `check` task on the root project
        // without pulling in the whole `java` plugin convention.
        project.getPluginManager().apply("base");
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(c -> c.dependsOn(check));

        // Make sure every project's classes are compiled before we try to
        // read them. Both `archguardCheck` and `archguardReport` need this.
        project.getGradle().projectsEvaluated(gradle -> project.getRootProject().getAllprojects().forEach(sp -> {
            var classes = sp.getTasks().findByName("classes");
            if (classes != null) {
                check.configure(c -> c.dependsOn(classes));
                report.configure(r -> r.dependsOn(classes));
            }
        }));
    }

    private static void wireCommon(ArchGuardCheckTask t, ArchGuardExtension ext) {
        t.getConfigFile().convention(ext.getConfigFile());
        t.getOutputDirectory().convention(ext.getOutputDirectory());
        t.getFailOnSeverity().convention(ext.getFailOnSeverity());
        t.getExcludedModules().convention(ext.getExcludedModules());
        t.getAllowPartialReactor().convention(ext.getAllowPartialReactor());
        t.getSkip().convention(ext.getSkip());
    }
}
