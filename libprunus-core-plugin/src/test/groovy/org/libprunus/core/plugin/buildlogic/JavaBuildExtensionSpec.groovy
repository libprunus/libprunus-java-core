package org.libprunus.core.plugin.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class JavaBuildExtensionSpec extends Specification {

    def "DEFAULT_JAVA_VERSION and DEFAULT_COVERAGE_THRESHOLD encode the project coverage and toolchain baseline"() {
        expect:
        JavaBuildExtension.DEFAULT_JAVA_VERSION == 25
        JavaBuildExtension.DEFAULT_COVERAGE_THRESHOLD == 0.9d
    }

    def "targetJavaVersion convention defaults to project Java baseline and reflects caller overrides"() {
        given:
        def extension = ProjectBuilder.builder().withName("java-build-extension-target-version").build()
                .objects.newInstance(JavaBuildExtension)

        expect:
        extension.targetJavaVersion.get() == 25

        when:
        extension.targetJavaVersion.set(21)

        then:
        extension.targetJavaVersion.get() == 21
    }

    def "instructionCoverageThreshold convention defaults to project coverage baseline and reflects caller overrides"() {
        given:
        def extension = ProjectBuilder.builder().withName("java-build-extension-instruction").build()
                .objects.newInstance(JavaBuildExtension)

        expect:
        extension.instructionCoverageThreshold.get() == 0.9d

        when:
        extension.instructionCoverageThreshold.set(0.85d)

        then:
        extension.instructionCoverageThreshold.get() == 0.85d
    }

    def "lineCoverageThreshold convention defaults to project coverage baseline and reflects caller overrides"() {
        given:
        def extension = ProjectBuilder.builder().withName("java-build-extension-line").build()
                .objects.newInstance(JavaBuildExtension)

        expect:
        extension.lineCoverageThreshold.get() == 0.9d

        when:
        extension.lineCoverageThreshold.set(0.80d)

        then:
        extension.lineCoverageThreshold.get() == 0.80d
    }

    def "branchCoverageThreshold convention defaults to project coverage baseline and reflects caller overrides"() {
        given:
        def extension = ProjectBuilder.builder().withName("java-build-extension-branch").build()
                .objects.newInstance(JavaBuildExtension)

        expect:
        extension.branchCoverageThreshold.get() == 0.9d

        when:
        extension.branchCoverageThreshold.set(0.70d)

        then:
        extension.branchCoverageThreshold.get() == 0.70d
    }
}
