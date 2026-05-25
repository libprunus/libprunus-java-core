package org.libprunus.core.plugin.aot.task

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class GenerateLibraryWhitelistTaskIntegrationSpec extends Specification {

    @TempDir
    File testProjectDir

    def "whitelist task remains up-to-date when only unrelated compile dependency content changes"() {
        given:
        def repoRoot = findRepoRoot()
        writeLibraryProject(testProjectDir, repoRoot)

        when:
        def firstResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('customWhitelistTask')
                .build()

        and:
        writeJar(new File(testProjectDir, 'libs/unrelated-dependency.jar'), 'sample/DependencyMarker.class', [2] as byte[])

        and:
        def secondResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('customWhitelistTask')
                .build()

        then:
        firstResult.task(':customWhitelistTask').outcome == TaskOutcome.SUCCESS
        secondResult.task(':customWhitelistTask').outcome == TaskOutcome.UP_TO_DATE
    }

    private static File findRepoRoot() {
        File current = new File(System.getProperty('user.dir')).canonicalFile
        while (current != null && !new File(current, 'settings.gradle.kts').exists()) {
            current = current.parentFile
        }
        assert current != null
        current
    }

    private static void writeLibraryProject(File projectDir, File repoRoot) {
        File libsDir = new File(projectDir, 'libs')
        libsDir.mkdirs()
        writeJar(new File(libsDir, 'unrelated-dependency.jar'), 'sample/DependencyMarker.class', [1] as byte[])

        String escapedRepoRoot = repoRoot.absolutePath.replace('\\', '\\\\')

        new File(projectDir, 'settings.gradle').text = """
rootProject.name = 'whitelist-up-to-date-sample'
includeBuild('${escapedRepoRoot}')
""".stripIndent()

        new File(projectDir, 'build.gradle').text = '''
plugins {
    id 'org.libprunus.libprunus-core-plugin'
}

group = 'sample'
version = '1.0.0'

prunus {
    aot {
        enabled = true
        logRegistryClass = 'sample.LogContextRegistry'
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.libprunus:libprunus-core:0.0.1-SNAPSHOT'
    implementation files('libs/unrelated-dependency.jar')
}

def whitelistTaskType = Class.forName('org.libprunus.core.plugin.aot.task.GenerateLibraryWhitelistTask')

tasks.register('customWhitelistTask', whitelistTaskType) {
    dependsOn tasks.named('classes')
    registryClass.set('sample.LogContextRegistry')
    targetCompatibility.set(JavaVersion.current().majorVersion)
    mainClassesDirs.from(sourceSets.main.output.classesDirs)
    runtimeClasspath.from(configurations.runtimeClasspath)
    outputDirectory.set(layout.buildDirectory.dir('generated/custom-whitelist'))
}
'''

        File sourceDir = new File(projectDir, 'src/main/java/sample')
        sourceDir.mkdirs()

        new File(sourceDir, 'LogContextRegistry.java').text = '''
package sample;

import org.libprunus.core.log.annotation.LogRegistry;

@LogRegistry
public class LogContextRegistry {
}
'''

        new File(sourceDir, 'SampleService.java').text = '''
package sample;

public class SampleService {
    public String echo(String text) {
        return text;
    }
}
'''
    }

    private static void writeJar(File jarFile, String entryPath, byte[] payload) {
        if (jarFile.parentFile != null) {
            jarFile.parentFile.mkdirs()
        }
        new JarOutputStream(new FileOutputStream(jarFile)).withCloseable { jarOut ->
            jarOut.putNextEntry(new JarEntry(entryPath))
            jarOut.write(payload)
            jarOut.closeEntry()
        }
    }
}
