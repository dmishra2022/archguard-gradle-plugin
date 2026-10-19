package io.archguard.gradle;

import io.archguard.api.Severity;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * DSL extension exposed to consumer build scripts as {@code archguard { ... }}.
 *
 * <h2>Minimal usage</h2>
 * <pre>{@code
 * archguard {
 *     // defaults: configFile = rootProject/architecture.yml
 *     //           outputDirectory = <project>/build/reports/archguard
 *     //           failOnSeverity = ERROR
 * }
 * }</pre>
 *
 * <h2>Full usage</h2>
 * <pre>{@code
 * archguard {
 *     configFile.set(layout.projectDirectory.file("architecture.yml"))
 *     outputDirectory.set(layout.buildDirectory.dir("reports/archguard"))
 *     failOnSeverity.set("ERROR")                                   // one of INFO / WARN / ERROR
 *     baseline.set(layout.projectDirectory.file("architecture-baseline.json"))
 *     suppressions.set(layout.projectDirectory.file("architecture-suppressions.json"))
 *     excludedModules.addAll(":some-module", ":another-module")
 *     allowPartialReactor.set(false)                                // true only when deliberately scoping
 *     skip.set(false)                                               // true turns the whole plugin off
 * }
 * }</pre>
 *
 * <p>Every property follows Gradle's lazy configuration conventions — they are
 * {@link Property}/{@link ListProperty} instances, so values can be supplied by
 * other tasks, by command-line {@code -P} flags, or by convention mappings.
 *
 * <h2>Why each property exists</h2>
 *
 * <ul>
 *   <li><b>configFile</b> — the YAML policy. Shared with the ArchGuard Maven
 *       plugin so a team using both tools has a single source of truth.</li>
 *   <li><b>outputDirectory</b> — where {@code archguard.json}, {@code
 *       archguard.sarif} and {@code index.html} are written. SARIF is readable
 *       by GitHub Code Scanning out of the box.</li>
 *   <li><b>failOnSeverity</b> — overrides {@code failOn} from the policy if
 *       set. Rarely needed; the policy is the usual place to decide this.</li>
 *   <li><b>baseline</b> — enables the "debt" mode: findings whose fingerprints
 *       are already on the baseline do not fail the build. New findings do.</li>
 *   <li><b>suppressions</b> — explicit, auditable {@code (fingerprint, owner,
 *       reason, expires)} entries; preferred over baselines for permanent waivers.</li>
 *   <li><b>excludedModules</b> — Gradle project paths (e.g. {@code
 *       :integration-tests}) that should not contribute their bytecode to the
 *       analysis. The equivalent of the Maven plugin's {@code excludedModules}.</li>
 *   <li><b>allowPartialReactor</b> — ArchGuard's safety rail. By default,
 *       running the check task without every subproject in the task graph is
 *       an error, because a partial scan can mask dependency rule violations.
 *       Set to true only when you have deliberately scoped the build.</li>
 *   <li><b>skip</b> — kill switch, equivalent to {@code -Darchguard.skip=true}
 *       in the Maven plugin. Primarily for emergency breakage recovery.</li>
 * </ul>
 */
public abstract class ArchGuardExtension {

    public static final String NAME = "archguard";

    private final RegularFileProperty configFile;
    private final DirectoryProperty   outputDirectory;
    private final Property<Severity>  failOnSeverity;
    private final RegularFileProperty baseline;
    private final RegularFileProperty baselineOutput;
    private final Property<Boolean>   overwriteBaseline;
    private final RegularFileProperty suppressions;
    private final ListProperty<String> excludedModules;
    private final Property<Boolean>   allowPartialReactor;
    private final Property<Boolean>   skip;

    @Inject
    public ArchGuardExtension(ObjectFactory objects) {
        this.configFile          = objects.fileProperty();
        this.outputDirectory     = objects.directoryProperty();
        this.failOnSeverity      = objects.property(Severity.class);
        this.baseline            = objects.fileProperty();
        this.baselineOutput      = objects.fileProperty();
        this.overwriteBaseline   = objects.property(Boolean.class).convention(false);
        this.suppressions        = objects.fileProperty();
        this.excludedModules     = objects.listProperty(String.class);
        this.allowPartialReactor = objects.property(Boolean.class).convention(false);
        this.skip                = objects.property(Boolean.class).convention(false);
    }

    public RegularFileProperty   getConfigFile()          { return configFile;          }
    public DirectoryProperty     getOutputDirectory()     { return outputDirectory;     }
    public Property<Severity>    getFailOnSeverity()      { return failOnSeverity;      }
    public RegularFileProperty   getBaseline()            { return baseline;            }
    public RegularFileProperty   getBaselineOutput()      { return baselineOutput;      }
    public Property<Boolean>     getOverwriteBaseline()   { return overwriteBaseline;   }
    public RegularFileProperty   getSuppressions()        { return suppressions;        }
    public ListProperty<String>  getExcludedModules()     { return excludedModules;     }
    public Property<Boolean>     getAllowPartialReactor() { return allowPartialReactor; }
    public Property<Boolean>     getSkip()                { return skip;                }
}
