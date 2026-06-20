package org.libprunus.core.plugin.buildlogic;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import info.solidsoft.gradle.pitest.PitestPlugin;
import info.solidsoft.gradle.pitest.PitestPluginExtension;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
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

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private static final List<String> COMPILER_ARGS =
            List.of("-parameters", "-Xlint:all,-serial,-processing,-classfile,-this-escape", "-Werror");

    private static final List<TestLogEvent> TEST_LOG_EVENTS =
            List.of(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED);

    private static final Properties TOOL_VERSIONS = loadToolVersions();

    private final Project project;
    private final JavaBuildExtension javaBuild;

    public JavaBuildLogic(Project project, JavaBuildExtension javaBuild) {
        this.project = project;
        this.javaBuild = javaBuild;
    }

    public void apply() {
        applyNecessaryPlugins();
        configureJava();
        configureJacoco();
        configureTest();
        configureSpotless();
        configureErrorProne();
        configurePitest();
    }

    private void applyNecessaryPlugins() {
        var pluginManager = project.getPluginManager();

        pluginManager.apply(JacocoPlugin.class);
        pluginManager.apply(JavaLibraryPlugin.class);
        pluginManager.apply(SpotlessPlugin.class);
        pluginManager.apply(ErrorPronePlugin.class);
    }

    private void configureJava() {
        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);

        javaExtension
                .getToolchain()
                .getLanguageVersion()
                .set(javaBuild.getTargetJavaVersion().map(JavaLanguageVersion::of));

        javaExtension.withSourcesJar();
        javaExtension.withJavadocJar();

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            var options = task.getOptions();
            options.setEncoding(UTF_8);
            options.getCompilerArgs().addAll(COMPILER_ARGS);
            options.getRelease().set(javaBuild.getTargetJavaVersion());
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
                new CoverageRuleSpec("INSTRUCTION", javaBuild.getInstructionCoverageThreshold()),
                new CoverageRuleSpec("LINE", javaBuild.getLineCoverageThreshold()),
                new CoverageRuleSpec("BRANCH", javaBuild.getBranchCoverageThreshold()));

        project.afterEvaluate(p -> project.getTasks()
                .withType(JacocoCoverageVerification.class)
                .configureEach(verification -> verification.getViolationRules().rule(rule -> {
                    rule.setEnabled(true);
                    for (CoverageRuleSpec spec : specs) {
                        addCoverageLimit(
                                rule, spec.jacocoCounter(), spec.threshold().get());
                    }
                })));
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

    private record CoverageRuleSpec(String jacocoCounter, Provider<Double> threshold) {}

    private void configureTest() {
        declareTestDependencies();
        configureTestTasks();
        configureSpock();
    }

    private void declareTestDependencies() {
        var dependencies = project.getDependencies();
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

    private void configureSpock() {
        project.afterEvaluate(evaluated -> {
            if (!javaBuild.getSpockEnabled().get()) {
                return;
            }
            project.getPluginManager().apply("groovy");
            var dependencies = project.getDependencies();
            dependencies.add("testImplementation", "org.apache.groovy:groovy:" + TOOL_VERSIONS.getProperty("groovy"));
            dependencies.add(
                    "testImplementation", "org.spockframework:spock-core:" + TOOL_VERSIONS.getProperty("spock"));
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
            kotlinGradleExtension.target("**/*.gradle.kts");
            kotlinGradleExtension.targetExclude("**/build/**");

            kotlinGradleExtension.trimTrailingWhitespace();
            kotlinGradleExtension.endWithNewline();
        });
    }

    private void configureErrorProne() {
        var dependencies = project.getDependencies();
        dependencies.add("api", "org.jspecify:jspecify:" + TOOL_VERSIONS.getProperty("jspecify"));
        dependencies.add(
                "errorprone", "com.google.errorprone:error_prone_core:" + TOOL_VERSIONS.getProperty("errorprone-core"));
        dependencies.add("errorprone", "com.uber.nullaway:nullaway:" + TOOL_VERSIONS.getProperty("nullaway"));

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            var errorProne =
                    ((ExtensionAware) task.getOptions()).getExtensions().getByType(ErrorProneOptions.class);
            errorProne.getDisableAllChecks().set(true);
            errorProne.error("NullAway");
            errorProne.option("NullAway:OnlyNullMarked", "true");
            errorProne.option("NullAway:JSpecifyMode", "true");
            if (!task.getName().endsWith("TestJava")) {
                errorProne.error("RequireExplicitNullMarking");
            }
        });
    }

    private void configurePitest() {
        project.afterEvaluate(evaluated -> {
            if (!javaBuild.getPitestEnabled().get()) {
                return;
            }
            project.getPluginManager().apply(PitestPlugin.class);
            var pitest = project.getExtensions().getByType(PitestPluginExtension.class);
            pitest.getPitestVersion().set(TOOL_VERSIONS.getProperty("pitest"));
            pitest.getJunit5PluginVersion().set(TOOL_VERSIONS.getProperty("pitest-junit5"));
            pitest.getMutationThreshold().set(javaBuild.getMutationThreshold());
            pitest.getOutputFormats().set(List.of("XML", "HTML"));
            pitest.getFailWhenNoMutations().set(false);
            pitest.getJvmArgs().add("--add-opens=java.base/java.lang=ALL-UNNAMED");

            var tasks = project.getTasks();
            var pitestTask = tasks.named(PitestPlugin.PITEST_TASK_NAME);
            pitestTask.configure(task -> task.mustRunAfter(tasks.withType(Test.class)));
            tasks.named("check").configure(task -> task.dependsOn(pitestTask));
        });
    }

    private static Properties loadToolVersions() {
        var versions = new Properties();
        try (var stream = JavaBuildLogic.class.getResourceAsStream("/libprunus-tool-versions.properties")) {
            if (stream == null) {
                throw new GradleException("libprunus-tool-versions.properties is missing from the plugin classpath");
            }
            versions.load(stream);
        } catch (IOException e) {
            throw new GradleException("Failed to read libprunus-tool-versions.properties", e);
        }
        return versions;
    }
}
