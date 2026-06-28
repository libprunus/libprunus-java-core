package org.libprunus.core.plugin.aot.log.contract

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.libprunus.core.plugin.testutil.IntegrationTestRepo

final class ContractProjectHarness {

    private static final String DEFAULT_LOG_REGISTRY_CLASS = 'contract.LogContextRegistry'
    private static final String DEFAULT_LOGBACK_XML = '''<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%-5level %logger{0} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
'''
    private static final List<String> DEFAULT_FIXTURE_RESOURCE_DIRS = [
            '/contract/fixtures',
            '/contract/fixtures-inheritance3',
    ]
    private static final List<String> DEFAULT_DTO_FQCNS = [
            'contract.AccessAndAnnotationMatrixDto',
            'contract.AccessAndAnnotationMatrixRecordDto',
            'contract.ClassSensitiveAccessAndAnnotationMatrixDto',
            'contract.ClassDoNotLogAccessAndAnnotationMatrixDto',
            'contract.ClassDoLogAccessAndAnnotationMatrixDto',
            'contract.ClassSensitiveAccessAndAnnotationMatrixRecordDto',
            'contract.ClassDoNotLogAccessAndAnnotationMatrixRecordDto',
            'contract.ClassDoLogAccessAndAnnotationMatrixRecordDto',
            'contract.ExtendedAccessAndAnnotationMatrixDto',
            'contract.ExtendedClassSensitiveAccessAndAnnotationMatrixDto',
            'contract.ExtendedClassDoNotLogAccessAndAnnotationMatrixDto',
            'contract.ExtendedClassDoLogAccessAndAnnotationMatrixDto',
            'contract.Inh3CPlainFromPPlainGpPlainSubject',
            'contract.Inh3CPlainFromPPlainGpSensitiveSubject',
            'contract.Inh3CPlainFromPPlainGpDoNotLogSubject',
            'contract.Inh3CPlainFromPPlainGpDoLogSubject',
            'contract.Inh3CPlainFromPSensitiveGpPlainSubject',
            'contract.Inh3CPlainFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CPlainFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CPlainFromPSensitiveGpDoLogSubject',
            'contract.Inh3CPlainFromPDoNotLogGpPlainSubject',
            'contract.Inh3CPlainFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CPlainFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CPlainFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CPlainFromPDoLogGpPlainSubject',
            'contract.Inh3CPlainFromPDoLogGpSensitiveSubject',
            'contract.Inh3CPlainFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CPlainFromPDoLogGpDoLogSubject',
            'contract.Inh3CSensitiveFromPPlainGpPlainSubject',
            'contract.Inh3CSensitiveFromPPlainGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPPlainGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPPlainGpDoLogSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpPlainSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpDoLogSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpPlainSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CSensitiveFromPDoLogGpPlainSubject',
            'contract.Inh3CSensitiveFromPDoLogGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPDoLogGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPPlainGpPlainSubject',
            'contract.Inh3CDoNotLogFromPPlainGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPPlainGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPPlainGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpPlainSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpPlainSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpPlainSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpDoLogSubject',
            'contract.Inh3CDoLogFromPPlainGpPlainSubject',
            'contract.Inh3CDoLogFromPPlainGpSensitiveSubject',
            'contract.Inh3CDoLogFromPPlainGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPPlainGpDoLogSubject',
            'contract.Inh3CDoLogFromPSensitiveGpPlainSubject',
            'contract.Inh3CDoLogFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CDoLogFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPSensitiveGpDoLogSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpPlainSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CDoLogFromPDoLogGpPlainSubject',
            'contract.Inh3CDoLogFromPDoLogGpSensitiveSubject',
            'contract.Inh3CDoLogFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPDoLogGpDoLogSubject',
    ]
    private static final List<String> DEFAULT_SERVICE_FQCNS = [
            'contract.CallsiteAccessMatrixService',
            'contract.ClassSensitiveCallsiteAccessMatrixService',
            'contract.ClassDoNotLogCallsiteAccessMatrixService',
            'contract.ClassDoLogCallsiteAccessMatrixService',
            'contract.ExtendedCallsiteAccessMatrixService',
            'contract.ExtendedClassSensitiveCallsiteAccessMatrixService',
            'contract.ExtendedClassDoNotLogCallsiteAccessMatrixService',
            'contract.ExtendedClassDoLogCallsiteAccessMatrixService',
            'contract.Inh3CPlainFromPPlainGpPlainSubject',
            'contract.Inh3CPlainFromPPlainGpSensitiveSubject',
            'contract.Inh3CPlainFromPPlainGpDoNotLogSubject',
            'contract.Inh3CPlainFromPPlainGpDoLogSubject',
            'contract.Inh3CPlainFromPSensitiveGpPlainSubject',
            'contract.Inh3CPlainFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CPlainFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CPlainFromPSensitiveGpDoLogSubject',
            'contract.Inh3CPlainFromPDoNotLogGpPlainSubject',
            'contract.Inh3CPlainFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CPlainFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CPlainFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CPlainFromPDoLogGpPlainSubject',
            'contract.Inh3CPlainFromPDoLogGpSensitiveSubject',
            'contract.Inh3CPlainFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CPlainFromPDoLogGpDoLogSubject',
            'contract.Inh3CSensitiveFromPPlainGpPlainSubject',
            'contract.Inh3CSensitiveFromPPlainGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPPlainGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPPlainGpDoLogSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpPlainSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPSensitiveGpDoLogSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpPlainSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CSensitiveFromPDoLogGpPlainSubject',
            'contract.Inh3CSensitiveFromPDoLogGpSensitiveSubject',
            'contract.Inh3CSensitiveFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CSensitiveFromPDoLogGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPPlainGpPlainSubject',
            'contract.Inh3CDoNotLogFromPPlainGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPPlainGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPPlainGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpPlainSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPSensitiveGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpPlainSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpPlainSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpSensitiveSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CDoNotLogFromPDoLogGpDoLogSubject',
            'contract.Inh3CDoLogFromPPlainGpPlainSubject',
            'contract.Inh3CDoLogFromPPlainGpSensitiveSubject',
            'contract.Inh3CDoLogFromPPlainGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPPlainGpDoLogSubject',
            'contract.Inh3CDoLogFromPSensitiveGpPlainSubject',
            'contract.Inh3CDoLogFromPSensitiveGpSensitiveSubject',
            'contract.Inh3CDoLogFromPSensitiveGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPSensitiveGpDoLogSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpPlainSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpSensitiveSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPDoNotLogGpDoLogSubject',
            'contract.Inh3CDoLogFromPDoLogGpPlainSubject',
            'contract.Inh3CDoLogFromPDoLogGpSensitiveSubject',
            'contract.Inh3CDoLogFromPDoLogGpDoNotLogSubject',
            'contract.Inh3CDoLogFromPDoLogGpDoLogSubject',
    ]

    static void writeBaseProject(File projectDir,
            List<String> dtoFqcns = DEFAULT_DTO_FQCNS,
            List<String> serviceFqcns = DEFAULT_SERVICE_FQCNS) {
        writeProject(projectDir, dtoFqcns, serviceFqcns,
                DEFAULT_FIXTURE_RESOURCE_DIRS, DEFAULT_LOG_REGISTRY_CLASS,
                DEFAULT_LOGBACK_XML, false)
    }

    static void writeBaseProjectWithFixtures(File projectDir,
                                             List<String> dtoFqcns,
                                             List<String> serviceFqcns,
                                             List<String> fixtureResourceDirs) {
        writeProject(projectDir, dtoFqcns, serviceFqcns,
                fixtureResourceDirs, DEFAULT_LOG_REGISTRY_CLASS,
                DEFAULT_LOGBACK_XML, false)
    }

    static void writeBaseProjectWithMultiPackageFixtures(File projectDir,
                                                        List<String> dtoFqcns,
                                                        List<String> serviceFqcns,
                                                        List<String> fixtureResourceDirs,
                                                        String logRegistryClass) {
        writeProject(projectDir, dtoFqcns, serviceFqcns,
                fixtureResourceDirs, logRegistryClass,
                DEFAULT_LOGBACK_XML, true)
    }

    static void writeBaseProjectWithFixturesAndCustomLogback(File projectDir,
                                                            List<String> dtoFqcns,
                                                            List<String> serviceFqcns,
                                                            List<String> fixtureResourceDirs,
                                                            String logRegistryClass,
                                                            String logbackXml) {
        writeProject(projectDir, dtoFqcns, serviceFqcns,
                fixtureResourceDirs, logRegistryClass,
                logbackXml, false)
    }

    static BuildResult runCapture(File projectDir) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments('captureContractResults')
                .build()
    }

    static BuildResult runBuildAndFail(File projectDir) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments('captureContractResults', '--stacktrace')
                .buildAndFail()
    }

    private static void writeProject(File projectDir,
                                     List<String> dtoFqcns,
                                     List<String> serviceFqcns,
                                     List<String> fixtureResourceDirs,
                                     String logRegistryClass,
                                     String logbackXml,
                                     boolean multiPackage) {
        def escapedRepo = IntegrationTestRepo.escapedPath()
        def version = IntegrationTestRepo.CORE_VERSION

        new File(projectDir, 'settings.gradle').text = """
rootProject.name = 'sample-app'
""".stripIndent()

        new File(projectDir, 'build.gradle').text = renderBuildGradle(escapedRepo, version, dtoFqcns, serviceFqcns, logRegistryClass)

        if (multiPackage) {
            writeMultiPackageFixtures(projectDir, fixtureResourceDirs)
        } else {
            writeSinglePackageFixtures(projectDir, fixtureResourceDirs)
        }

        def resourcesDir = new File(projectDir, 'src/main/resources')
        resourcesDir.mkdirs()
        new File(resourcesDir, 'logback.xml').text = logbackXml
    }

    private static String renderBuildGradle(String escapedRepo,
                                            String version,
                                            List<String> dtoFqcns,
                                            List<String> serviceFqcns,
                                            String logRegistryClass) {
        def dtoFqcnsLiteral = dtoFqcns.collect { "'${it}'" }.join(', ')
        def serviceFqcnsLiteral = serviceFqcns.collect { "'${it}'" }.join(', ')
        """
plugins {
    id 'org.libprunus.libprunus-core-plugin'
}

prunus {
    aot {
        enabled = true
        logRegistryClass = '${logRegistryClass}'
    }
}

repositories {
    maven { url '${escapedRepo}'; metadataSources { artifact() } }
    mavenCentral()
}

dependencies {
    implementation "org.libprunus:libprunus-core:${version}"
    implementation 'org.slf4j:slf4j-api:2.0.18'
    runtimeOnly 'ch.qos.logback:logback-classic:1.5.16'
}

tasks.withType(JavaCompile).configureEach {
    options.errorprone.disable('RequireExplicitNullMarking')
}

def mainClassesDir = layout.buildDirectory.dir('classes/java/main')
def mainRuntimeClasspath = sourceSets.main.runtimeClasspath
def dtoFqcns = [${dtoFqcnsLiteral}]
def serviceFqcns = [${serviceFqcnsLiteral}]

tasks.register('captureContractResults') {
    dependsOn tasks.named('classes')
    outputs.dir(layout.buildDirectory.dir('contract-results'))
    doLast {
        def runtimeUrls = mainRuntimeClasspath.files.collect { it.toURI().toURL() } as URL[]
        def loader = new URLClassLoader(runtimeUrls, ClassLoader.systemClassLoader)
        def resultsDir = layout.buildDirectory.dir('contract-results').get().asFile
        resultsDir.mkdirs()
        def loggableType = loader.loadClass('org.libprunus.core.log.runtime.Loggable')
        def logRuntime = loader.loadClass('org.libprunus.core.log.runtime.LogRuntime')
        def maxLenField = logRuntime.getDeclaredField('boundMaxMessageLength')
        maxLenField.setAccessible(true)
        maxLenField.setInt(null, 4096)

        dtoFqcns.each { String fqcn ->
            def simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
            try {
                def cls = loader.loadClass(fqcn)
                def instance = cls.getDeclaredConstructor().newInstance()
                new File(resultsDir, "\${simpleName}.tostring.txt").write(instance.toString(), 'UTF-8')
                new File(resultsDir, "\${simpleName}.loggable.txt").write(String.valueOf(loggableType.isAssignableFrom(cls)), 'UTF-8')
            } catch (Throwable t) {
                new File(resultsDir, "\${simpleName}.error.txt").write(t.getClass().getName() + ': ' + (t.message ?: '<no message>'), 'UTF-8')
            }
        }

        serviceFqcns.each { String fqcn ->
            def simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
            try {
                def cls = loader.loadClass(fqcn)
                def callsite = cls.getMethod('invokeAll')
                String captured = (String) callsite.invoke(null)
                new File(resultsDir, "\${simpleName}.callsite.txt").write(captured, 'UTF-8')
                new File(resultsDir, "\${simpleName}.loggable.txt").write(String.valueOf(loggableType.isAssignableFrom(cls)), 'UTF-8')
            } catch (Throwable t) {
                new File(resultsDir, "\${simpleName}.error.txt").write(t.getClass().getName() + ': ' + (t.message ?: '<no message>'), 'UTF-8')
            }
        }
    }
}
"""
    }

    private static void writeSinglePackageFixtures(File projectDir, List<String> fixtureResourceDirs) {
        def sourceDir = new File(projectDir, 'src/main/java/contract')
        sourceDir.mkdirs()
        fixtureResourceDirs.each { String resourcePath ->
            def fixtureUrl = ContractProjectHarness.getResource(resourcePath)
            assert fixtureUrl, "fixture resource root not found: ${resourcePath}"
            def fixtureRoot = new File(fixtureUrl.toURI())
            fixtureRoot.listFiles({ File f -> f.name.endsWith('.java') } as FileFilter).each { javaFile ->
                new File(sourceDir, javaFile.name).text = javaFile.text
            }
        }
    }

    private static void writeMultiPackageFixtures(File projectDir, List<String> fixtureResourceDirs) {
        def javaRoot = new File(projectDir, 'src/main/java')
        javaRoot.mkdirs()
        def packageDeclaration = ~/(?m)^\s*package\s+([\w.]+)\s*;/
        fixtureResourceDirs.each { String resourcePath ->
            def fixtureUrl = ContractProjectHarness.getResource(resourcePath)
            assert fixtureUrl, "fixture resource root not found: ${resourcePath}"
            def fixtureRoot = new File(fixtureUrl.toURI())
            fixtureRoot.listFiles({ File f -> f.name.endsWith('.java') } as FileFilter).each { javaFile ->
                def text = javaFile.text
                def matcher = packageDeclaration.matcher(text)
                assert matcher.find(), "fixture ${javaFile.name} is missing a package declaration"
                def pkg = matcher.group(1)
                def targetDir = new File(javaRoot, pkg.replace('.', '/'))
                targetDir.mkdirs()
                new File(targetDir, javaFile.name).text = text
            }
        }
    }
}
