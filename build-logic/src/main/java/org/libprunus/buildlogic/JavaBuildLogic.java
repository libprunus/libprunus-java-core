package org.libprunus.buildlogic;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.sonarqube.gradle.SonarQubePlugin;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;

final class JavaBuildLogic {

    private static final int JAVA_VERSION = 25;
    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final double COVERAGE_THRESHOLD = 0.9;

    private static final List<String> COMPILER_ARGS =
            List.of("-parameters", "-Xlint:all,-serial,-processing,-classfile,-this-escape", "-Werror");

    private static final List<TestLogEvent> TEST_LOG_EVENTS =
            List.of(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED);

    private final Project project;
    private final VersionCatalog libs;

    JavaBuildLogic(Project project) {
        this(project, null);
    }

    JavaBuildLogic(Project project, VersionCatalog libs) {
        this.project = project;
        this.libs = libs;
    }

    void apply() {
        applyNecessaryPlugins();
        configureJava();
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

        project.getRootProject().getPluginManager().apply(SonarQubePlugin.class);
    }

    private void configureJava() {
        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        javaExtension.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(JAVA_VERSION));

        javaExtension.withSourcesJar();
        javaExtension.withJavadocJar();

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            var options = task.getOptions();
            options.setEncoding(UTF_8);
            options.getCompilerArgs().addAll(COMPILER_ARGS);
            options.getRelease().set(JAVA_VERSION);
            options.setIncremental(true);
        });

        project.getTasks().withType(Javadoc.class).configureEach(task -> {
            var options = (StandardJavadocDocletOptions) task.getOptions();
            options.setEncoding(UTF_8);
            options.setCharSet(UTF_8);
            options.setDocEncoding(UTF_8);
        });
    }

    private void configureJacoco() {
        configureJacocoReportFormats();
        configureJacocoCoverageThresholds();
        bindJacocoVerificationToCheck();
    }

    private void configureJacocoReportFormats() {
        project.getTasks().withType(JacocoReport.class).configureEach(report -> {
            var reports = report.getReports();
            reports.getCsv().getRequired().set(false);
            reports.getHtml().getRequired().set(true);
            reports.getXml().getRequired().set(true);
        });
    }

    private void configureJacocoCoverageThresholds() {
        List<CoverageRuleSpec> specs = List.of(
                new CoverageRuleSpec("INSTRUCTION", COVERAGE_THRESHOLD),
                new CoverageRuleSpec("LINE", COVERAGE_THRESHOLD),
                new CoverageRuleSpec("BRANCH", COVERAGE_THRESHOLD));

        project.getTasks().withType(JacocoCoverageVerification.class).configureEach(verification ->
                verification.getViolationRules().rule(rule -> {
                    rule.setEnabled(true);
                    for (CoverageRuleSpec spec : specs) {
                        addCoverageLimit(rule, spec.jacocoCounter(), spec.threshold());
                    }
                }));
    }

    private void bindJacocoVerificationToCheck() {
        var tasks = project.getTasks();
        tasks.named("check").configure(task -> task.dependsOn(tasks.withType(JacocoCoverageVerification.class)));
    }

    private void addCoverageLimit(JacocoViolationRule rule, String counter, double threshold) {
        rule.limit(limit -> {
            limit.setCounter(counter);
            limit.setValue("COVEREDRATIO");
            limit.setMinimum(BigDecimal.valueOf(threshold));
        });
    }

    private record CoverageRuleSpec(String jacocoCounter, double threshold) {}

    private void configureTest() {
        declareTestDependencies();
        configureTestTasks();
    }

    private void declareTestDependencies() {
        var dependencies = project.getDependencies();

        if (libs != null) {
            libs.findLibrary("groovy-core").ifPresent(dep -> dependencies.add("testImplementation", dep));
            libs.findLibrary("spock-core").ifPresent(dep -> dependencies.add("testImplementation", dep));
        }
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter");
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher");
    }

    private void configureTestTasks() {
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            test.setMaxParallelForks(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            test.testLogging(testLogging -> {
                testLogging.setEvents(TEST_LOG_EVENTS);
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

        spotless.groovy(groovyExtension -> {
            groovyExtension.target("src/**/*.groovy");
            groovyExtension.targetExclude("**/build/generated/**/*.groovy");

            groovyExtension.importOrder();
            groovyExtension.removeSemicolons();
            groovyExtension.trimTrailingWhitespace();
            groovyExtension.leadingTabsToSpaces(4);
            groovyExtension.endWithNewline();
        });

        spotless.kotlinGradle(kotlinGradleExtension -> {
            kotlinGradleExtension.target("**/*.gradle.kts");
            kotlinGradleExtension.targetExclude("**/build/**");

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
