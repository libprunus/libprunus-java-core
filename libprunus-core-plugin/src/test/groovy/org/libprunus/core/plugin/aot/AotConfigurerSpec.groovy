package org.libprunus.core.plugin.aot

import java.nio.file.Path
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension
import spock.lang.Specification

class AotConfigurerSpec extends Specification {

    def "apply afterEvaluate validation accepts non-blank logRegistryClass when enabled is true"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-validate-ok").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        noExceptionThrown()
        project.tasks.findByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) != null
        project.tasks.findByName(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK) != null
        project.tasks.findByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK) != null
    }

    def "apply afterEvaluate raises ProjectConfigurationException wrapping IllegalStateException when logRegistryClass is blank"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-validate-blank-type-${blank.replace(' ', '_').replace('\t', 't').replace('\n', 'n') ?: 'empty'}").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set(blank)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        def ex = thrown(org.gradle.api.ProjectConfigurationException)
        ex.cause instanceof IllegalStateException

        where:
        blank << ["", "   ", "\t\n"]
    }

    def "apply afterEvaluate exception message guides remediation when logRegistryClass is blank"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-validate-blank-message").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("")

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        def ex = thrown(org.gradle.api.ProjectConfigurationException)
        ex.cause.message.contains("logRegistryClass must be set when prunus.aot.enabled is true")
        ex.cause.message.contains("Either set logRegistryClass to a fully-qualified @LogRegistry class name")
        ex.cause.message.contains("leave enabled at its default (false) to disable AOT entirely")
    }

    def "apply afterEvaluate failure does not roll back previously registered tasks"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-validate-blank-tasks-intact").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("")

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        thrown(org.gradle.api.ProjectConfigurationException)
        project.tasks.findByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) != null
        project.tasks.findByName(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK) != null
        project.tasks.findByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK) != null
    }

    def "apply afterEvaluate validation reads provider-backed enabled flag at evaluation time"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-validate-provider-enabled").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(project.providers.provider { true })
        aot.logRegistryClass.set("")

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        def ex = thrown(org.gradle.api.ProjectConfigurationException)
        ex.cause instanceof IllegalStateException
        ex.cause.message.contains("logRegistryClass must be set when prunus.aot.enabled is true")
    }

    def "apply afterEvaluate validation is skipped when enabled is false even if logRegistryClass is blank"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-validate-skipped-${blank.replace(' ', '_') ?: 'empty'}").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(false)
        aot.logRegistryClass.set(blank)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def generateTask = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)

        then:
        noExceptionThrown()
        project.tasks.findByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) != null
        !generateTask.onlyIf.isSatisfiedBy(generateTask)

        where:
        blank << ["", "   "]
    }

    def "apply fails fast when Java plugin extension is absent"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-no-java-plugin").build()
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")

        when:
        new AotConfigurer(project, aot, javaBuild).apply()

        then:
        thrown(UnknownDomainObjectException)
        project.tasks.findByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) == null
        project.tasks.findByName(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK) == null
        project.tasks.findByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK) == null
    }

    def "apply fails when main source set is removed"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-no-main-source-set").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension)
        javaExtension.sourceSets.remove(javaExtension.sourceSets.getByName("main"))
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        def ex = thrown(UnknownDomainObjectException)
        ex.message.contains("main") || (ex.cause != null && ex.cause.message.contains("main"))
    }

    def "portableRelativePath returns forward-slash relative path on POSIX"() {
        given:
        def projectDir = Path.of("/proj/root")
        def target = Path.of("/proj/root/build/classes/java/main")

        when:
        def result = AotConfigurer.portableRelativePath(projectDir, target)

        then:
        result == "build/classes/java/main"
        !result.contains("\\")
    }

    def "getJavaExtension returns JavaPluginExtension when Java plugin is applied"() {
        given:
        def project = ProjectBuilder.builder().withName("gje-with-java").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def configurer = new AotConfigurer(project, project.objects.newInstance(AotExtension), null)

        when:
        def result = configurer.getJavaExtension()

        then:
        result instanceof JavaPluginExtension
        result.sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME) != null
    }

    def "getMainSourceSet returns main SourceSet when Java plugin is applied"() {
        given:
        def project = ProjectBuilder.builder().withName("gmss-normal").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def configurer = new AotConfigurer(project, project.objects.newInstance(AotExtension), null)

        when:
        def result = configurer.getMainSourceSet()

        then:
        (result as SourceSet).name == SourceSet.MAIN_SOURCE_SET_NAME
    }

    def "resolveCoordinate unwraps providers and returns null when the resolved value is null"() {
        given:
        def project = ProjectBuilder.builder().withName("resolve-coordinate").build()

        expect:
        AotConfigurer.resolveCoordinate(null) == null
        AotConfigurer.resolveCoordinate(project.providers.provider { null }) == null
        AotConfigurer.resolveCoordinate("plain") == "plain"
        AotConfigurer.resolveCoordinate(project.providers.provider { "wrapped" }) == "wrapped"
    }

    def "resolveCoordinate propagates exceptions from provider evaluation without swallowing them"() {
        given:
        def project = ProjectBuilder.builder().withName("resolve-coordinate-throwing").build()
        def failingProvider = project.providers.provider { throw new RuntimeException("boom") }

        when:
        AotConfigurer.resolveCoordinate(failingProvider)

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("boom")
    }

}
