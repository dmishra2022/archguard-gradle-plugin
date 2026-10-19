package io.archguard.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for the plugin using {@link ProjectBuilder}. Does not spin up a
 * real Gradle build — enough to verify the plugin registers the extension and
 * tasks correctly.
 */
class ArchGuardPluginTest {

    @Test
    void applies_cleanly_and_registers_extension_and_tasks() {
        Project root = ProjectBuilder.builder().build();
        root.getPluginManager().apply("io.archguard.gradle");

        assertThat(root.getExtensions().findByName(ArchGuardExtension.NAME))
                .as("archguard extension registered")
                .isInstanceOf(ArchGuardExtension.class);

        assertThat(root.getTasks().findByName(ArchGuardPlugin.CHECK_TASK_NAME))
                .as(ArchGuardPlugin.CHECK_TASK_NAME + " task registered")
                .isInstanceOf(ArchGuardCheckTask.class);

        assertThat(root.getTasks().findByName(ArchGuardPlugin.REPORT_TASK_NAME))
                .as(ArchGuardPlugin.REPORT_TASK_NAME + " task registered")
                .isInstanceOf(ArchGuardReportTask.class);

        assertThat(root.getTasks().findByName(ArchGuardPlugin.BASELINE_TASK_NAME))
                .as(ArchGuardPlugin.BASELINE_TASK_NAME + " task registered")
                .isInstanceOf(ArchGuardBaselineTask.class);
    }

    @Test
    void applying_to_a_subproject_is_rejected() {
        Project root = ProjectBuilder.builder().build();
        Project sub  = ProjectBuilder.builder().withParent(root).build();

        assertThatThrownBy(() -> sub.getPluginManager().apply("io.archguard.gradle"))
                .getRootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("root project");
    }

    @Test
    void extension_defaults_are_wired() {
        Project root = ProjectBuilder.builder().build();
        root.getPluginManager().apply("io.archguard.gradle");

        ArchGuardExtension ext = (ArchGuardExtension) root.getExtensions().getByName(ArchGuardExtension.NAME);

        assertThat(ext.getConfigFile().get().getAsFile().getName())
                .as("default config file name")
                .isEqualTo("architecture.yml");

        assertThat(ext.getBaselineOutput().get().getAsFile().getName())
                .as("default baseline output file name")
                .isEqualTo("architecture-baseline.json");

        assertThat(ext.getSkip().get()).isFalse();
        assertThat(ext.getOverwriteBaseline().get()).isFalse();
        assertThat(ext.getAllowPartialReactor().get()).isFalse();
    }

    @Test
    void check_task_wired_into_check_lifecycle() {
        Project root = ProjectBuilder.builder().build();
        root.getPluginManager().apply("io.archguard.gradle");

        var checkLifecycle = root.getTasks().findByName("check");
        assertThat(checkLifecycle)
                .as("base plugin's `check` task should exist after our plugin applies it")
                .isNotNull();

        boolean dependsOnArchGuardCheck = checkLifecycle.getTaskDependencies().getDependencies(checkLifecycle).stream()
                .anyMatch(t -> t.getName().equals(ArchGuardPlugin.CHECK_TASK_NAME));

        assertThat(dependsOnArchGuardCheck)
                .as("check lifecycle must depend on archguardCheck so `./gradlew build` runs it")
                .isTrue();
    }
}
