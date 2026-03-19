package org.libprunus.buildlogic;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;

final class JavaBuildLogic {
    private static final double COVERAGE_THRESHOLD = 0.9;

    private final Project project;

    JavaBuildLogic(Project project) {
        this.project = project;
    }

    void apply() {
        applyNecessaryPlugins();
        configureJacoco();
        configureTest();
        configureSpotless();

        configureInternalBom();
    }

    private void applyNecessaryPlugins() {
        var pluginManager = project.getPluginManager();

        pluginManager.apply(GroovyPlugin.class);
        pluginManager.apply(JacocoPlugin.class);
        pluginManager.apply(JavaLibraryPlugin.class);
        pluginManager.apply(SpotlessPlugin.class);
    }

    private void configureJacoco() {
        var tasks = project.getTasks();

        tasks.withType(JacocoReport.class).configureEach(report -> {
            var reports = report.getReports();
            reports.getCsv().getRequired().set(false);
            reports.getHtml().getRequired().set(true);
            reports.getXml().getRequired().set(true);
        });

        tasks.withType(JacocoCoverageVerification.class).configureEach(verification ->
                verification.getViolationRules().rule(rule -> {
                    rule.setEnabled(true);
                    addCoverageLimit(rule, "BRANCH");
                    addCoverageLimit(rule, "LINE");
                }));

        tasks.named("check")
                .configure(task -> task.dependsOn(tasks.withType(JacocoCoverageVerification.class)));
    }

    private void addCoverageLimit(JacocoViolationRule rule, String counter) {
        rule.limit(limit -> {
            limit.setCounter(counter);
            limit.setValue("COVEREDRATIO");
            limit.setMinimum(BigDecimal.valueOf(COVERAGE_THRESHOLD));
        });
    }

    private void configureTest() {
        var dependencies = project.getDependencies();

        dependencies.add("testImplementation", "org.apache.groovy:groovy");
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter");
        dependencies.add("testImplementation", "org.spockframework:spock-core");
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher");

        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            test.setMaxParallelForks(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            test.testLogging(testLogging -> {
                testLogging.setEvents(List.of(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED));
                testLogging.setExceptionFormat(TestExceptionFormat.FULL);
            });
            test.finalizedBy(project.getTasks().withType(JacocoReport.class));
        });
    }

    private void configureSpotless() {
        var spotless = project.getExtensions().getByType(SpotlessExtension.class);

        spotless.java(javaExtension -> {
            javaExtension.target("src/**/*.java");
            javaExtension.targetExclude("**/build/generated/**/*.java");

            javaExtension.palantirJavaFormat();
            javaExtension.removeUnusedImports();
            javaExtension.importOrder();
            javaExtension.trimTrailingWhitespace();
            javaExtension.endWithNewline();
        });

        spotless.kotlinGradle(kotlinGradleExtension -> {
            kotlinGradleExtension.target(
                    project.fileTree(project.getProjectDir()).matching(patterns -> {
                        patterns.include("**/*.gradle.kts");
                        patterns.exclude("**/build/**");
                    }));

            kotlinGradleExtension.trimTrailingWhitespace();
            kotlinGradleExtension.endWithNewline();
        });
    }

    private void configureInternalBom() {
        var dependencies = project.getDependencies();

        dependencies.add(
                "api",
                dependencies.platform(dependencies.project(Map.of("path", ":libprunus-bom"))));
    }
}
