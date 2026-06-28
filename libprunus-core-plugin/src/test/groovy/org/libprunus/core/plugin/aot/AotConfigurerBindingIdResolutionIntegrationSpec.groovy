package org.libprunus.core.plugin.aot

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class AotConfigurerBindingIdResolutionIntegrationSpec extends Specification {

    @TempDir
    File testProjectDir

    def "generateAotBinding uses resolved provider coordinate values for bindingId"() {
        given:
        writeSampleProjectWithProviderCoordinates(testProjectDir, "org.provider", "2.0.0", true, null)

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('assertMainBindingIdFromResolvedCoordinates')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        result.output.contains(':assertMainBindingIdFromResolvedCoordinates')
    }

    def "generateAotBinding normalizes unicode-surrounded provider coordinates before hashing"() {
        given:
        writeSampleProjectWithProviderCoordinates(testProjectDir, " org.provider ", " 2.0.0 ", true, null)

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('assertMainBindingIdFromResolvedCoordinates')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        result.output.contains(':assertMainBindingIdFromResolvedCoordinates')
    }

    def "bindingId resolves at LIBRARY mode without binding generateAotBinding to the build lifecycle"() {
        given:
        writeSampleProjectWithProviderCoordinates(testProjectDir, "org.provider", "2.0.0", true, "LIBRARY")

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('assertMainBindingIdFromResolvedCoordinates', 'assertGenerateAotBindingOnlyIfNotSatisfied')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
        result.output.contains(':assertMainBindingIdFromResolvedCoordinates')
        result.output.contains(':assertGenerateAotBindingOnlyIfNotSatisfied')
    }

    private static void writeSampleProjectWithProviderCoordinates(
            File projectDir,
            String providerGroup,
            String providerVersion,
            boolean enabled,
            String modeName) {
        new File(projectDir, 'settings.gradle').text = """
rootProject.name = 'binding-provider-app'
""".stripIndent().trim() + "\n"

        def escapedGroup = providerGroup
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        def escapedVersion = providerVersion
            .replace("\\", "\\\\")
            .replace("'", "\\'")

        def modeAssignment = modeName == null
            ? ""
            : "        mode = org.libprunus.core.plugin.aot.AotMode.${modeName}\n"
        def libraryAssertionTask = modeName == "LIBRARY" ? """
tasks.register('assertGenerateAotBindingOnlyIfNotSatisfied') {
    doLast {
        def task = tasks.named('generateAotBinding').get()
        def satisfied = task.onlyIf.isSatisfiedBy(task)
        if (satisfied) {
            throw new GradleException('Expected generateAotBinding onlyIf to be unsatisfied in LIBRARY mode but was satisfied')
        }
    }
}
""" : ""

        new File(projectDir, 'build.gradle').text = """
plugins {
    id 'java'
    id 'org.libprunus.libprunus-core-plugin'
}

group = providers.provider { '${escapedGroup}' }
version = providers.provider { '${escapedVersion}' }

prunus {
    aot {
        enabled = ${enabled}
        logRegistryClass = 'sample.Registry'
${modeAssignment}    }
}

tasks.register('assertMainBindingIdFromResolvedCoordinates') {
    doLast {
        def actual = tasks.named('generateAotBinding').get().bindingId.get()
        def expected = new org.libprunus.core.plugin.aot.BindingIdGenerator()
                .generate('org.provider', project.name, '2.0.0', project.path, 'main')
        if (actual != expected) {
            throw new GradleException('Expected bindingId ' + expected + ' but was ' + actual)
        }
    }
}
${libraryAssertionTask}
""".stripIndent().trim() + "\n"

        File srcDir = new File(projectDir, 'src/main/java/sample')
        srcDir.mkdirs()
        new File(srcDir, 'Registry.java').text = """
package sample;

public final class Registry {
    public static String provider() {
        return "noop";
    }
}
""".stripIndent().trim() + "\n"
    }
}
