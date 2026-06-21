package org.libprunus.buildlogic;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import info.solidsoft.gradle.pitest.PitestPlugin;
import info.solidsoft.gradle.pitest.PitestPluginExtension;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.cyclonedx.Version;
import org.cyclonedx.gradle.CyclonedxDirectTask;
import org.cyclonedx.gradle.CyclonedxPlugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.GroovyPlugin;
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
import org.sonarqube.gradle.SonarQubePlugin;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import com.github.jk1.license.LicenseReportExtension;
import com.github.jk1.license.LicenseReportPlugin;
import com.github.jk1.license.filter.DependencyFilter;
import com.github.jk1.license.filter.LicenseBundleNormalizer;

final class JavaBuildLogic {

    private static final int JAVA_VERSION = 25;
    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final double COVERAGE_THRESHOLD = 0.9;
    private static final int MUTATION_THRESHOLD = 70;
    private static final String API = "api";
    private static final String ERRORPRONE = "errorprone";
    private static final String TEST_IMPLEMENTATION = "testImplementation";
    private static final String TEST_RUNTIME_ONLY = "testRuntimeOnly";
    private static final String RUNTIME_CLASSPATH = "runtimeClasspath";
    private static final String CHECK_TASK = "check";
    private static final String CYCLONEDX_DIRECT_BOM_TASK = "cyclonedxDirectBom";
    private static final String CHECK_LICENSE_TASK = "checkLicense";
    private static final String SBOM_REPORT_PATH = "reports/sbom/bom.json";
    private static final String ALLOWED_LICENSES_FILE = "config/allowed-licenses.json";

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
        configureErrorProne();
        configurePitest();

        configureInternalBom();
        configureSbomAndLicenseGate();
    }

    private void applyNecessaryPlugins() {
        var pluginManager = project.getPluginManager();

        pluginManager.apply(GroovyPlugin.class);
        pluginManager.apply(JacocoPlugin.class);
        pluginManager.apply(JavaLibraryPlugin.class);
        pluginManager.apply(SpotlessPlugin.class);
        pluginManager.apply(CyclonedxPlugin.class);
        pluginManager.apply(LicenseReportPlugin.class);

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
        tasks.named(CHECK_TASK).configure(task -> task.dependsOn(tasks.withType(JacocoCoverageVerification.class)));
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
            libs.findLibrary("groovy-core").ifPresent(dep -> dependencies.add(TEST_IMPLEMENTATION, dep));
            libs.findLibrary("spock-core").ifPresent(dep -> dependencies.add(TEST_IMPLEMENTATION, dep));
        }
        dependencies.add(TEST_IMPLEMENTATION, "org.junit.jupiter:junit-jupiter");
        dependencies.add(TEST_RUNTIME_ONLY, "org.junit.platform:junit-platform-launcher");
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

    private void configureErrorProne() {
        if (libs == null) {
            return;
        }
        project.getPluginManager().apply(ErrorPronePlugin.class);
        var dependencies = project.getDependencies();
        libs.findLibrary("jspecify").ifPresent(dep -> dependencies.add(API, dep));
        libs.findLibrary("errorprone-core").ifPresent(dep -> dependencies.add(ERRORPRONE, dep));
        libs.findLibrary("nullaway").ifPresent(dep -> dependencies.add(ERRORPRONE, dep));

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
        if (libs == null) {
            return;
        }
        project.getPluginManager().apply(PitestPlugin.class);
        var pitest = project.getExtensions().getByType(PitestPluginExtension.class);
        libs.findVersion("pitest-core")
                .ifPresent(version -> pitest.getPitestVersion().set(version.getRequiredVersion()));
        libs.findVersion("pitest-junit5")
                .ifPresent(version -> pitest.getJunit5PluginVersion().set(version.getRequiredVersion()));
        pitest.getMutationThreshold().set(MUTATION_THRESHOLD);
        pitest.getOutputFormats().set(List.of("XML", "HTML"));
        pitest.getFailWhenNoMutations().set(false);
        pitest.getJvmArgs().add("--add-opens=java.base/java.lang=ALL-UNNAMED");

        var tasks = project.getTasks();
        var pitestTask = tasks.named(PitestPlugin.PITEST_TASK_NAME);
        pitestTask.configure(task -> task.mustRunAfter(tasks.withType(Test.class)));
        tasks.named(CHECK_TASK).configure(task -> task.dependsOn(pitestTask));
    }

    private void configureInternalBom() {
        var dependencies = project.getDependencies();

        dependencies.add(
                API,
                dependencies.platform(dependencies.project(Map.of("path", ":libprunus-bom"))));
    }

    private void configureSbomAndLicenseGate() {
        // java-platform (the BOM) has no runtimeClasspath; nothing to scan or gate.
        if (project.getConfigurations().findByName(RUNTIME_CLASSPATH) == null) {
            return;
        }
        configureCycloneDxSbom();
        configureLicenseGate();
    }

    private void configureCycloneDxSbom() {
        Provider<RegularFile> sbomJson =
                project.getLayout().getBuildDirectory().file(SBOM_REPORT_PATH);

        var tasks = project.getTasks();
        tasks.named(CYCLONEDX_DIRECT_BOM_TASK, CyclonedxDirectTask.class).configure(task -> {
            task.getIncludeConfigs().set(List.of(RUNTIME_CLASSPATH));
            task.getSchemaVersion().set(Version.VERSION_16);
            task.getJsonOutput().set(sbomJson);
            task.getXmlOutput().unsetConvention();
        });
        tasks.named(CHECK_TASK).configure(task -> task.dependsOn(CYCLONEDX_DIRECT_BOM_TASK));
    }

    private void configureLicenseGate() {
        LicenseReportExtension licenseReport =
                project.getExtensions().getByType(LicenseReportExtension.class);
        licenseReport.allowedLicensesFile = project.getRootProject().file(ALLOWED_LICENSES_FILE);
        licenseReport.configurations = new String[] {RUNTIME_CLASSPATH};
        licenseReport.filters = new DependencyFilter[] {new LicenseBundleNormalizer()};

        project.getTasks().named(CHECK_TASK).configure(task -> task.dependsOn(CHECK_LICENSE_TASK));
    }
}
