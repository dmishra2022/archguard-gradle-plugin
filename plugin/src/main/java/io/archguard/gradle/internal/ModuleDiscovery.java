package io.archguard.gradle.internal;

import io.archguard.engine.ModuleInput;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Translates a Gradle project tree into the {@code (modules, classpath)} pair
 * the ArchGuard engine expects.
 *
 * <h2>Why this is its own class</h2>
 *
 * The Maven plugin has one and only one shape of inputs: {@code MavenProject}
 * instances in a reactor. Gradle is more flexible — you can have included
 * builds, Android variants, Kotlin multiplatform targets, platforms, non-JVM
 * subprojects, and so on. Keeping the discovery in one place means the growing
 * list of edge cases does not pollute the task implementation.
 *
 * <h2>What counts as a "module" for ArchGuard</h2>
 *
 * <ul>
 *   <li>Any Gradle subproject that applies the {@code java} plugin.</li>
 *   <li>Its {@code main} source set's compiled classes directory.</li>
 *   <li>Identified by its Gradle path (e.g. {@code :order-adapter-rest}) so
 *       findings refer to the thing a developer would type into {@code
 *       ./gradlew :order-adapter-rest:test}.</li>
 * </ul>
 *
 * <p>Non-Java subprojects (pure platforms, documentation, Android, …) are
 * silently skipped — ArchGuard analyses bytecode, so nothing to analyse means
 * nothing to say.
 *
 * <h2>What counts as "external classpath"</h2>
 *
 * Dependencies that are not themselves modules in this build. We collect the
 * runtime classpath of every included module and subtract the module class
 * directories we already know about. This matches the Maven plugin's behaviour:
 * ArchGuard needs the external jars on its analysis classpath so it can
 * resolve supertype chains, but it must not try to classify those jars as
 * modules in the policy.
 */
public final class ModuleDiscovery {

    private ModuleDiscovery() {}

    /** Result record returned by {@link #discover}. */
    public record Discovery(List<ModuleInput> modules, List<Path> externalClasspath,
                            List<String> skippedProjects) {
    }

    public static Discovery discover(Project rootProject, Set<String> excludedProjectPaths) {
        List<ModuleInput> modules = new ArrayList<>();
        Set<Path> ownedClassDirs = new LinkedHashSet<>();
        Set<Path> external        = new LinkedHashSet<>();
        List<String> skipped      = new ArrayList<>();
        Path workspace            = rootProject.getRootDir().toPath().toAbsolutePath().normalize();

        for (Project p : rootProject.getAllprojects()) {
            if (excludedProjectPaths.contains(p.getPath())) {
                skipped.add(p.getPath() + " (excluded via archguard.excludedModules)");
                continue;
            }
            JavaPluginExtension javaExt = p.getExtensions().findByType(JavaPluginExtension.class);
            if (javaExt == null) {
                skipped.add(p.getPath() + " (not a java project)");
                continue;
            }
            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            var mainSs = sourceSets.findByName("main");
            if (mainSs == null) {
                skipped.add(p.getPath() + " (no main source set)");
                continue;
            }

            // Classes directory. We take the aggregated .classes/java/main (and
            // kotlin/main etc) via SourceSet.getOutput().
            Directory primary = p.getLayout().getBuildDirectory()
                    .dir("classes/java/main").getOrNull();
            Path classesDir = primary != null ? primary.getAsFile().toPath() : null;
            if (classesDir == null || !Files.exists(classesDir)) {
                // Covers Kotlin-only modules where the Java directory is empty.
                File firstOutput = mainSs.getOutput().getClassesDirs().getFiles().stream().findFirst().orElse(null);
                if (firstOutput == null) {
                    skipped.add(p.getPath() + " (no compiled classes on disk; run `assemble` first)");
                    continue;
                }
                classesDir = firstOutput.toPath();
            }
            classesDir = classesDir.toAbsolutePath().normalize();
            ownedClassDirs.add(classesDir);

            List<Path> sourceRoots = new ArrayList<>();
            mainSs.getAllJava().getSrcDirs().forEach(d -> sourceRoots.add(d.toPath()));

            modules.add(new ModuleInput(p.getPath(), classesDir, sourceRoots, workspace));
        }

        // External classpath: every runtime jar that isn't one of our module
        // class directories.
        for (Project p : rootProject.getAllprojects()) {
            Configuration runtime = p.getConfigurations().findByName("runtimeClasspath");
            if (runtime == null || !runtime.isCanBeResolved()) continue;
            try {
                for (File f : runtime.resolve()) {
                    Path abs = f.toPath().toAbsolutePath().normalize();
                    if (!ownedClassDirs.contains(abs)) external.add(abs);
                }
            } catch (RuntimeException ignored) {
                // Unresolvable configurations (constraints-only, variants
                // without a target, etc.) are intentionally skipped. The
                // analysis simply gets a smaller classpath and ArchGuard will
                // report any resulting UnresolvedType diagnostics itself.
            }
        }

        return new Discovery(
                Collections.unmodifiableList(modules),
                new ArrayList<>(external),
                Collections.unmodifiableList(skipped));
    }
}
