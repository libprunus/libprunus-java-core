package org.libprunus.core.plugin.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import java.math.BigDecimal
import java.nio.file.Files
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule
import spock.lang.Specification
import spock.lang.Subject

class JavaBuildLogicSpec extends Specification {

    @Subject
    JavaBuildLogic subject

    Project project

    def "apply executes plugin application before all four configure groups without error"() {
        given:
        project = createProject("apply-smoke")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))

        when:
        subject.apply()

        then:
        noExceptionThrown()
    }

    def "applyNecessaryPlugins applies jacoco java library and spotless plugins"() {
        given:
        project = createProject("apply-plugins")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))

        when:
        subject.applyNecessaryPlugins()

        then:
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.plugins.hasPlugin(SpotlessPlugin)
    }

    def "configureJava sets encoding compiler args and release on compile tasks when realized"() {
        given:
        project = createProject("configure-java-compile")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureJava()

        then:
        project.tasks.withType(JavaCompile).every { task ->
            task.options.encoding == "UTF-8" &&
                    task.options.compilerArgs.containsAll(["-parameters", "-Xlint:all,-serial,-processing,-classfile,-this-escape", "-Werror"]) &&
                    task.options.release.get() == 25
        }
    }

    def "configureJava propagates configured targetJavaVersion to compile release and toolchain language version"() {
        given:
        project = createProject("configure-java-target-version-${version}")
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        javaBuild.targetJavaVersion.set(version)
        subject = new JavaBuildLogic(project, javaBuild)
        subject.applyNecessaryPlugins()

        when:
        subject.configureJava()

        then:
        project.tasks.withType(JavaCompile).every { task ->
            task.options.release.get() == version
        }
        project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion.get() == JavaLanguageVersion.of(version)

        where:
        version << [21, 25]
    }

    def "configureJava sets encoding charset and doc encoding on javadoc tasks when realized"() {
        given:
        project = createProject("configure-java-javadoc")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureJava()

        then:
        project.tasks.withType(Javadoc).every { task ->
            def opts = task.options as StandardJavadocDocletOptions
            opts.encoding == "UTF-8" &&
                    opts.charSet == "UTF-8" &&
                    opts.docEncoding == "UTF-8"
        }
    }

    def "configureJava registers sources and javadoc jar archive tasks"() {
        given:
        project = createProject("configure-java-archive-jars")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureJava()

        then:
        project.tasks.findByName("sourcesJar") != null
        project.tasks.findByName("javadocJar") != null
    }

    def "configureJacoco enables html and xml reports and explicitly disables csv report"() {
        given:
        project = createProject("configure-jacoco-reports")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureJacoco()

        then:
        project.tasks.withType(JacocoReport).every { report ->
            report.reports.html.required.get() &&
                    report.reports.xml.required.get() &&
                    !report.reports.csv.required.get()
        }
    }

    def "configureJacoco registers exactly instruction line and branch limits with default threshold"() {
        given:
        project = createProject("configure-jacoco-default-limits-${extraVerifications}-${invokeTimes}")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()
        (1..extraVerifications).each { index ->
            project.tasks.create("extraVerification${index}", JacocoCoverageVerification)
        }

        when:
        (1..invokeTimes).each {
            subject.configureJacoco()
        }
        ((ProjectInternal) project).evaluate()

        then:
        project.tasks.withType(JacocoCoverageVerification).every { verification ->
            def triples = verification.violationRules.rules
                    .collectMany { rule ->
                        rule.limits.collect { limit ->
                            [String.valueOf(limit.counter), String.valueOf(limit.value), limit.minimum]
                        }
                    }
                    .toSet()
            def counters = triples.collect { it[0] }.toSet()

            triples.size() == 3 &&
                    triples.contains(["INSTRUCTION", "COVEREDRATIO", BigDecimal.valueOf(0.9d)]) &&
                    triples.contains(["LINE", "COVEREDRATIO", BigDecimal.valueOf(0.9d)]) &&
                    triples.contains(["BRANCH", "COVEREDRATIO", BigDecimal.valueOf(0.9d)]) &&
                    !counters.contains("CLASS") &&
                    !counters.contains("METHOD") &&
                    !counters.contains("COMPLEXITY")
        }

        where:
        [extraVerifications, invokeTimes] << [[0, 2], [1, 2]].combinations()
    }

    def "configureJacoco wires every JacocoCoverageVerification task as a check dependency"() {
        given:
        project = createProject("configure-jacoco-check-deps-${extraVerifications}")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()
        (1..extraVerifications).each { index ->
            project.tasks.create("extraVerification${index}", JacocoCoverageVerification)
        }

        when:
        subject.configureJacoco()
        ((ProjectInternal) project).evaluate()

        then:
        def checkTask = project.tasks.named("check").get()
        def checkDependencies = checkTask.taskDependencies.getDependencies(checkTask)
        checkDependencies.containsAll(project.tasks.withType(JacocoCoverageVerification).toSet())

        where:
        extraVerifications << [0, 2]
    }

    def "configureJacoco resolves instruction line and branch thresholds from javaBuildExtension"() {
        given:
        project = createProject("configure-jacoco-thresholds-${instructionThreshold}-${lineThreshold}-${branchThreshold}")
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        javaBuild.instructionCoverageThreshold.set(instructionThreshold)
        javaBuild.lineCoverageThreshold.set(lineThreshold)
        javaBuild.branchCoverageThreshold.set(branchThreshold)
        subject = new JavaBuildLogic(project, javaBuild)
        subject.applyNecessaryPlugins()
        project.tasks.create("verification-${instructionThreshold}-${lineThreshold}-${branchThreshold}", JacocoCoverageVerification)

        when:
        subject.configureJacoco()
        ((ProjectInternal) project).evaluate()

        then:
        def limits = project.tasks.withType(JacocoCoverageVerification).collectMany { v ->
            v.violationRules.rules.collectMany { r ->
                r.limits.collect { l -> [String.valueOf(l.counter), l.minimum] }
            }
        }
        limits.contains(["INSTRUCTION", BigDecimal.valueOf(instructionThreshold)])
        limits.contains(["LINE", BigDecimal.valueOf(lineThreshold)])
        limits.contains(["BRANCH", BigDecimal.valueOf(branchThreshold)])

        where:
        instructionThreshold | lineThreshold | branchThreshold
        0.85d                | 0.80d         | 0.70d
        1.0d                 | 0.5d          | 0.0d
    }

    def "configureJacoco enables the registered violation rule so coverage limits are enforced"() {
        given:
        project = createProject("configure-jacoco-rule-enabled")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureJacoco()
        ((ProjectInternal) project).evaluate()

        then:
        project.tasks.withType(JacocoCoverageVerification).every { verification ->
            !verification.violationRules.rules.isEmpty() &&
                    verification.violationRules.rules.every { rule -> rule.enabled }
        }
    }

    def "addCoverageLimit appends expected threshold for each counter"() {
        given:
        project = createProject("add-limit-${counter}-${threshold}-${seedExistingRule}")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()
        def verification = project.tasks.create("verification-${counter}-${threshold}-${seedExistingRule}", JacocoCoverageVerification)
        def rule = createRule(verification, seedExistingRule)

        when:
        subject.addCoverageLimit(rule, counter, threshold)

        then:
        rule.limits.count {
            String.valueOf(it.counter) == counter &&
                    String.valueOf(it.value) == "COVEREDRATIO" &&
                    it.minimum == BigDecimal.valueOf(threshold)
        } == 1

        where:
        [counter, threshold, seedExistingRule] << [
                ["LINE", "BRANCH", "INSTRUCTION"],
                [0.5d, 0.9d, 1.0d],
                [false, true]
        ].combinations()
    }

    def "configureTest registers only junit platform dependencies"() {
        given:
        project = createProject("configure-test-junit-only")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureTest()

        then:
        def testImpl = dependencyCoordinates(project, "testImplementation")
        testImpl.contains("org.junit.jupiter:junit-jupiter")
        dependencyCoordinates(project, "testRuntimeOnly").contains("org.junit.platform:junit-platform-launcher")
    }

    def "configureTest does not impose spock or groovy on testImplementation"() {
        given:
        project = createProject("configure-test-no-spock-groovy")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureTest()

        then:
        def testImpl = dependencyCoordinates(project, "testImplementation")
        !testImpl.contains("org.apache.groovy:groovy")
        !testImpl.contains("org.spockframework:spock-core")
    }

    def "configureTest configures Test tasks to use JUnit Platform"() {
        given:
        project = createProject("configure-test-junit-platform")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureTest()

        then:
        project.tasks.withType(Test).every { testTask -> testTask.options instanceof JUnitPlatformOptions }
    }

    def "configureTest sets maxParallelForks to half of available processors clamped at 1"() {
        given:
        project = createProject("configure-test-parallelism")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()
        def expectedMaxParallelForks = Math.max(1, Runtime.runtime.availableProcessors().intdiv(2))

        when:
        subject.configureTest()

        then:
        project.tasks.withType(Test).every { testTask -> testTask.maxParallelForks == expectedMaxParallelForks }
    }

    def "configureTest enables passed skipped and failed test log events"() {
        given:
        project = createProject("configure-test-log-events")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()
        def expectedEvents = EnumSet.of(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) as Set

        when:
        subject.configureTest()

        then:
        project.tasks.withType(Test).every { testTask -> testTask.testLogging.events == expectedEvents }
    }

    def "configureTest sets FULL exception format on test logging"() {
        given:
        project = createProject("configure-test-exception-format")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureTest()

        then:
        project.tasks.withType(Test).every { testTask -> testTask.testLogging.exceptionFormat == TestExceptionFormat.FULL }
    }

    def "configureTest binds Jacoco report tasks as finalizers of Test tasks"() {
        given:
        project = createProject("configure-test-jacoco-finalizer")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        subject.configureTest()

        then:
        project.tasks.withType(Test).every { testTask ->
            testTask.finalizedBy.getDependencies(testTask).containsAll(project.tasks.withType(JacocoReport).toSet())
        }
    }

    def "configureSpotless registers java and kotlin gradle spotless tasks"() {
        given:
        project = createProject("configure-spotless-${invokeTimes}")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()

        when:
        (1..invokeTimes).each {
            subject.configureSpotless()
        }

        then:
        project.tasks.findByName("spotlessJava") != null
        project.tasks.findByName("spotlessJavaCheck") != null
        project.tasks.findByName("spotlessKotlinGradle") != null
        project.tasks.findByName("spotlessKotlinGradleCheck") != null

        where:
        invokeTimes << [1, 2]
    }

    def "configureSpotless applies java format actions to the registered java format"() {
        given:
        project = createProject("configure-spotless-java-format")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()
        subject.configureSpotless()
        def spotless = project.extensions.getByType(SpotlessExtension)
        def javaFormat = spotless.@formats["java"]

        when:
        javaFormat.@lazyActions.each { it.execute(javaFormat) }

        then:
        javaFormat.@target != null
        javaFormat.@targetExclude != null
        javaFormat.@steps.size() >= 5
    }

    def "configureSpotless applies kotlin gradle include and exclude patterns to the registered kotlin format"() {
        given:
        project = createProject("configure-spotless-kotlin-format")
        createFile(project, "build.gradle.kts")
        createFile(project, "gradle/conventions.gradle.kts")
        createFile(project, "build/generated/ignored.gradle.kts")
        subject = new JavaBuildLogic(project, project.objects.newInstance(JavaBuildExtension))
        subject.applyNecessaryPlugins()
        subject.configureSpotless()
        def spotless = project.extensions.getByType(SpotlessExtension)
        def kotlinGradleFormat = spotless.@formats["kotlinGradle"]

        when:
        kotlinGradleFormat.@lazyActions.each { it.execute(kotlinGradleFormat) }
        def includedFiles = kotlinGradleFormat.@target.files.collect {
            project.projectDir.toPath().relativize(it.toPath()).toString().replace('\\', '/')
        }.toSet()

        then:
        includedFiles.contains("build.gradle.kts")
        includedFiles.contains("gradle/conventions.gradle.kts")
        !includedFiles.contains("build/generated/ignored.gradle.kts")
        kotlinGradleFormat.@steps.size() >= 2
    }

    private static JacocoViolationRule createRule(JacocoCoverageVerification verification, boolean seedExistingRule) {
        verification.violationRules.rule { rule ->
            if (seedExistingRule) {
                rule.limit { limit ->
                    limit.counter = "CLASS"
                    limit.value = "MISSEDCOUNT"
                    limit.minimum = BigDecimal.ZERO
                }
            }
        }
        verification.violationRules.rules.last()
    }

    private static Project createProject(String name) {
        ProjectBuilder.builder().withName(name).build()
    }

    private static Set<String> dependencyCoordinates(Project project, String configurationName) {
        project.configurations.getByName(configurationName).allDependencies.collect {
            "${it.group}:${it.name}".toString()
        }.toSet()
    }

    private static void createFile(Project project, String relativePath) {
        def path = project.projectDir.toPath().resolve(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, "plugins {}\n")
    }
}
