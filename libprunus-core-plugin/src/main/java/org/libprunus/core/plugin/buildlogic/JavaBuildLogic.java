package org.libprunus.core.plugin.buildlogic;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

public final class JavaBuildLogic {

    private static final int JAVA_VERSION = 25;
    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final double COVERAGE_THRESHOLD = 0.9;

    private final Project project;
    private final VersionCatalog libs;

    public JavaBuildLogic(Project project) {
        this(project, null);
    }

    public JavaBuildLogic(Project project, VersionCatalog libs) {
        this.project = project;
        this.libs = libs;
    }

    public void apply() {
        applyNecessaryPlugins();
        configureJava();
        configureJacoco();
        configureTest();
        configureSpotless();
    }

    private void applyNecessaryPlugins() {
        var pluginManager = project.getPluginManager();

        pluginManager.apply(GroovyPlugin.class);
        pluginManager.apply(JacocoPlugin.class);
        pluginManager.apply(JavaLibraryPlugin.class);
        pluginManager.apply(SpotlessPlugin.class);
    }

    private void configureJava() {
        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        javaExtension.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(JAVA_VERSION));

        javaExtension.withSourcesJar();
        javaExtension.withJavadocJar();

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            var options = task.getOptions();
            options.setEncoding(UTF_8);
            options.getCompilerArgs().add("-parameters");
            options.getCompilerArgs().addAll(List.of("-Xlint:unchecked", "-Xlint:deprecation"));
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
        var tasks = project.getTasks();

        tasks.withType(JacocoReport.class).configureEach(report -> {
            var reports = report.getReports();
            reports.getCsv().getRequired().set(false);
            reports.getHtml().getRequired().set(true);
            reports.getXml().getRequired().set(true);
        });

        tasks.withType(JacocoCoverageVerification.class)
                .configureEach(verification -> verification.getViolationRules().rule(rule -> {
                    rule.setEnabled(true);
                    addCoverageLimit(rule, "BRANCH");
                    addCoverageLimit(rule, "LINE");
                }));

        tasks.named("check").configure(task -> task.dependsOn(tasks.withType(JacocoCoverageVerification.class)));
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

        if (libs != null) {
            libs.findLibrary("groovy-core").ifPresent(dep -> dependencies.add("testImplementation", dep));
            libs.findLibrary("spock-core").ifPresent(dep -> dependencies.add("testImplementation", dep));
        }
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter");
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
            kotlinGradleExtension.target(
                    project.fileTree(project.getProjectDir()).matching(patterns -> {
                        patterns.include("**/*.gradle.kts");
                        patterns.exclude("**/build/**");
                    }));

            kotlinGradleExtension.trimTrailingWhitespace();
            kotlinGradleExtension.endWithNewline();
        });
    }
}
