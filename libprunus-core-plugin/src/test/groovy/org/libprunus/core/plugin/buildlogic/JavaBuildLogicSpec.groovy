package org.libprunus.core.plugin.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import java.math.BigDecimal
import java.nio.file.Files
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.external.javadoc.StandardJavadocDocletOptions
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

    def "applyNecessaryPlugins applies jacoco java library and spotless plugins"() {
        given: "a project and JavaBuildLogic instance"
        project = createProject("apply-plugins")
        subject = new JavaBuildLogic(project)

        when: "necessary plugins are applied"
        subject.applyNecessaryPlugins()

        then: "jacoco, java-library, and spotless plugins are present"
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.plugins.hasPlugin(SpotlessPlugin)
    }

    def "configureJacoco sets report formats and verification rule wiring"() {
        given: "a project with jacoco tasks and optional extra tasks"
        project = createProject("configure-jacoco-${extraReports}-${extraVerifications}-${invokeTimes}")
        subject = new JavaBuildLogic(project)
        subject.applyNecessaryPlugins()
        (1..extraReports).each { index ->
            project.tasks.create("extraReport${index}", JacocoReport)
        }
        (1..extraVerifications).each { index ->
            project.tasks.create("extraVerification${index}", JacocoCoverageVerification)
        }

        when: "jacoco configuration is applied multiple times"
        (1..invokeTimes).each {
            subject.configureJacoco()
        }

        then: "reports, violation rules, and check wiring are configured"
        project.tasks.withType(JacocoReport).every { report ->
            report.reports.html.required.get() &&
                    report.reports.xml.required.get() &&
                    !report.reports.csv.required.get()
        }

        project.tasks.withType(JacocoCoverageVerification).every { verification ->
            def counters = verification.violationRules.rules
                    .collectMany { it.limits.collect { limit -> String.valueOf(limit.counter) } }
                    .toSet()
            def values = verification.violationRules.rules
                    .collectMany { it.limits.collect { limit -> String.valueOf(limit.value) } }
                    .toSet()
            def minimums = verification.violationRules.rules
                    .collectMany { it.limits.collect { limit -> limit.minimum } }
                    .findAll { it != null }
                    .toSet()

            counters.contains("LINE") &&
                    counters.contains("BRANCH") &&
                    values.contains("COVEREDRATIO") &&
                    minimums.contains(BigDecimal.valueOf(0.9d))
        }

        def checkTask = project.tasks.named("check").get()
        def checkDependencies = checkTask.taskDependencies.getDependencies(checkTask)
        checkDependencies.containsAll(project.tasks.withType(JacocoCoverageVerification).toSet())

        where:
        [extraReports, extraVerifications, invokeTimes] << [[0, 2], [0, 2], [1, 2]].combinations()
    }

    def "addCoverageLimit appends expected threshold for each counter"() {
        given: "a jacoco violation rule that may already contain limits"
        project = createProject("add-limit-${counter}-${seedExistingRule}")
        subject = new JavaBuildLogic(project)
        subject.applyNecessaryPlugins()
        def verification = project.tasks.create("verification-${counter}-${seedExistingRule}", JacocoCoverageVerification)
        def rule = createRule(verification, seedExistingRule)

        when: "a coverage limit is added for the counter"
        subject.addCoverageLimit(rule, counter)

        then: "the expected threshold is appended exactly once"
        rule.limits.count {
            String.valueOf(it.counter) == counter &&
                    String.valueOf(it.value) == "COVEREDRATIO" &&
                    it.minimum == BigDecimal.valueOf(0.9d)
        } == 1

        where:
        [counter, seedExistingRule] << [["LINE", "BRANCH", "INSTRUCTION"], [false, true]].combinations()
    }

    def "configureSpotless registers java and kotlin gradle spotless tasks"() {
        given: "a project with spotless plugin applied"
        project = createProject("configure-spotless-${invokeTimes}")
        subject = new JavaBuildLogic(project)
        subject.applyNecessaryPlugins()

        when: "spotless configuration is applied multiple times"
        (1..invokeTimes).each {
            subject.configureSpotless()
        }

        then: "spotless java and kotlin gradle tasks are registered"
        project.tasks.findByName("spotlessJava") != null
        project.tasks.findByName("spotlessJavaCheck") != null
        project.tasks.findByName("spotlessKotlinGradle") != null
        project.tasks.findByName("spotlessKotlinGradleCheck") != null

        where:
        invokeTimes << [1, 2]
    }

    def "configureSpotless applies java format actions to the registered java format"() {
        given: "a registered spotless java format"
        project = createProject("configure-spotless-java-format")
        subject = new JavaBuildLogic(project)
        subject.applyNecessaryPlugins()
        subject.configureSpotless()
        def spotless = project.extensions.getByType(SpotlessExtension)
        def javaFormat = spotless.@formats["java"]

        when: "all java format lazy actions are executed"
        javaFormat.@lazyActions.each { it.execute(javaFormat) }

        then: "java format target, exclude, and formatter steps are configured"
        javaFormat.@target != null
        javaFormat.@targetExclude != null
        javaFormat.@steps.size() >= 5
    }

    def "configureSpotless applies kotlin gradle include and exclude patterns to the registered kotlin format"() {
        given: "a project containing gradle kotlin files inside and outside build directories"
        project = createProject("configure-spotless-kotlin-format")
        createFile(project, "build.gradle.kts")
        createFile(project, "gradle/conventions.gradle.kts")
        createFile(project, "build/generated/ignored.gradle.kts")
        subject = new JavaBuildLogic(project)
        subject.applyNecessaryPlugins()
        subject.configureSpotless()
        def spotless = project.extensions.getByType(SpotlessExtension)
        def kotlinGradleFormat = spotless.@formats["kotlinGradle"]

        when: "all kotlin gradle format lazy actions are executed"
        kotlinGradleFormat.@lazyActions.each { it.execute(kotlinGradleFormat) }
        def includedFiles = kotlinGradleFormat.@target.files.collect {
            project.projectDir.toPath().relativize(it.toPath()).toString().replace('\\', '/')
        }.toSet()

        then: "include and exclude patterns select only the expected gradle kts files"
        includedFiles.contains("build.gradle.kts")
        includedFiles.contains("gradle/conventions.gradle.kts")
        !includedFiles.contains("build/generated/ignored.gradle.kts")
        kotlinGradleFormat.@steps.size() >= 2
    }

    def "configureTest adds groovy junit and spock dependencies"() {
        given: "a project configured by JavaBuildLogic"
        project = createProject("configure-test-dependencies")
        subject = new JavaBuildLogic(project)
        subject.applyNecessaryPlugins()

        when: "test dependency configuration is applied"
        subject.configureTest()

        then: "groovy, junit, spock, and junit launcher dependencies are present"
        dependencyCoordinates(project, "testImplementation").containsAll([
                "org.apache.groovy:groovy",
                "org.junit.jupiter:junit-jupiter",
                "org.spockframework:spock-core"
        ])
        dependencyCoordinates(project, "testRuntimeOnly").contains("org.junit.platform:junit-platform-launcher")
    }

    def "configureTest configures junit platform logging parallelism and jacoco finalizers"() {
        given: "a project with test tasks"
        project = createProject("configure-test-task")
        subject = new JavaBuildLogic(project)
        subject.applyNecessaryPlugins()
        def expectedMaxParallelForks = Math.max(1, Runtime.runtime.availableProcessors().intdiv(2))

        when: "test task configuration is applied"
        subject.configureTest()

        then: "test tasks use junit platform, logging, parallelism, and jacoco finalizers"
        project.tasks.withType(Test).every { testTask ->
            testTask.options instanceof JUnitPlatformOptions &&
                    testTask.maxParallelForks == expectedMaxParallelForks &&
                    testTask.testLogging.events.containsAll([TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED]) &&
                    testTask.testLogging.exceptionFormat == TestExceptionFormat.FULL &&
                    testTask.finalizedBy.getDependencies(testTask).containsAll(project.tasks.withType(JacocoReport).toSet())
        }
    }

    def "apply configures complete build logic contract"() {
        given: "a fresh project"
        project = createProject("apply-contract")
        subject = new JavaBuildLogic(project)

        when: "the full build logic is applied"
        subject.apply()

        then: "plugin, jacoco, and spotless contracts are satisfied"
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.plugins.hasPlugin(SpotlessPlugin)

        project.tasks.withType(JacocoReport).every { report ->
            report.reports.html.required.get() &&
                    report.reports.xml.required.get() &&
                    !report.reports.csv.required.get()
        }

        project.tasks.findByName("spotlessJava") != null
        project.tasks.findByName("spotlessKotlinGradle") != null
    }

        def "configureJava sets encoding compiler args release and incremental on compile tasks when realized"() {
            given: "a project with necessary plugins applied"
            project = createProject("configure-java-compile")
            subject = new JavaBuildLogic(project)
            subject.applyNecessaryPlugins()

            when: "java configuration is applied"
            subject.configureJava()

            then: "all java compile tasks have encoding compiler args release and incremental configured"
            project.tasks.withType(JavaCompile).every { task ->
                task.options.encoding == "UTF-8" &&
                        task.options.compilerArgs.containsAll(["-parameters", "-Xlint:unchecked", "-Xlint:deprecation"]) &&
                        task.options.release.get() == 25 &&
                        task.options.incremental
            }
        }

        def "configureJava sets encoding charset and doc encoding on javadoc tasks when realized"() {
            given: "a project with necessary plugins applied"
            project = createProject("configure-java-javadoc")
            subject = new JavaBuildLogic(project)
            subject.applyNecessaryPlugins()

            when: "java configuration is applied"
            subject.configureJava()

            then: "all javadoc tasks have encoding charset and doc encoding configured"
            project.tasks.withType(Javadoc).every { task ->
                def opts = task.options as StandardJavadocDocletOptions
                opts.encoding == "UTF-8" &&
                        opts.charSet == "UTF-8" &&
                        opts.docEncoding == "UTF-8"
            }
        }

        def "configureSpotless applies groovy format actions to the registered groovy format"() {
            given: "a registered spotless groovy format"
            project = createProject("configure-spotless-groovy-format")
            subject = new JavaBuildLogic(project)
            subject.applyNecessaryPlugins()
            subject.configureSpotless()
            def spotless = project.extensions.getByType(SpotlessExtension)
            def groovyFormat = spotless.@formats["groovy"]

            when: "all groovy format lazy actions are executed"
            groovyFormat.@lazyActions.each { it.execute(groovyFormat) }

            then: "groovy format target exclude and formatter steps are configured"
            groovyFormat.@target != null
            groovyFormat.@targetExclude != null
            groovyFormat.@steps.size() >= 5
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
