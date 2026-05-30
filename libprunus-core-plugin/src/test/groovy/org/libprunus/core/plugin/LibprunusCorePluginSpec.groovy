package org.libprunus.core.plugin

import net.bytebuddy.build.gradle.AbstractByteBuddyTask
import org.gradle.api.Task
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.libprunus.core.plugin.aot.AotExtension
import org.libprunus.core.plugin.aot.AotMode
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import org.libprunus.core.plugin.aot.task.GenerateLibraryWhitelistTask
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension
import spock.lang.Specification

class LibprunusCorePluginSpec extends Specification {

    def "apply registers prunus extension with nested aot and javaBuild"() {
        given:
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-extension").build()
        def plugin = new LibprunusCorePlugin()

        when:
        plugin.apply(project)

        then:
        def prunus = project.extensions.findByName("prunus")
        prunus instanceof PrunusExtension
        ((PrunusExtension) prunus).aot instanceof AotExtension
        ((PrunusExtension) prunus).javaBuild instanceof JavaBuildExtension
    }

    def "apply wires javaBuildLogic side effects (jacoco plus java-library plus spotless tasks)"() {
        given:
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-javabuild").build()
        def plugin = new LibprunusCorePlugin()

        when:
        plugin.apply(project)
        project.evaluate()

        then:
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.tasks.findByName("spotlessJava") != null
        project.tasks.findByName("spotlessKotlinGradle") != null
    }

    def "apply wires aotConfigurer side effects (byteBuddy plus three aot tasks) when aot is enabled"() {
        given:
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-aot").build()
        def plugin = new LibprunusCorePlugin()

        when:
        plugin.apply(project)
        def prunus = (PrunusExtension) project.extensions.getByName("prunus")
        prunus.aot.enabled.set(true)
        prunus.aot.logRegistryClass.set("com.example.SampleRegistry")
        project.evaluate()

        then:
        project.tasks.findByName("byteBuddy") != null
        project.tasks.findByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) != null
        project.tasks.findByName(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK) != null
        project.tasks.findByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK) != null
    }

    def "apply does not leak unrelated build infrastructure (groovy plugin and spotlessGroovy task absent)"() {
        given:
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-no-leak").build()
        def plugin = new LibprunusCorePlugin()

        when:
        plugin.apply(project)
        project.evaluate()

        then:
        !project.plugins.hasPlugin(GroovyPlugin)
        project.tasks.findByName("spotlessGroovy") == null
    }

    def "apply does not register nested aot and javaBuild as project extensions"() {
        given:
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-no-nested-leak").build()
        def plugin = new LibprunusCorePlugin()

        when:
        plugin.apply(project)

        then:
        project.extensions.findByName("aot") == null
        project.extensions.findByName("javaBuild") == null
    }

    def "prunus dsl forwards aot and javaBuild closures to nested extensions"() {
        given:
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-dsl").build()
        new LibprunusCorePlugin().apply(project)
        def prunus = (PrunusExtension) project.extensions.getByName("prunus")

        when:
        prunus.aot { aot ->
            aot.enabled.set(true)
            aot.logRegistryClass.set("sample.Registry")
        }
        prunus.javaBuild { javaBuild ->
            javaBuild.targetJavaVersion.set(21)
        }

        then:
        prunus.aot.enabled.get()
        prunus.aot.logRegistryClass.get() == "sample.Registry"
        prunus.javaBuild.targetJavaVersion.get() == 21
    }

    def "aot pipeline tasks are not registered when aot.enabled is false regardless of mode"() {
        given:
        def project = ProjectBuilder.builder().withName("aot-pipeline-disabled-${mode}").build()
        def plugin = new LibprunusCorePlugin()

        when:
        plugin.apply(project)
        def prunus = (PrunusExtension) project.extensions.getByName("prunus")
        prunus.aot.enabled.set(false)
        prunus.aot.logRegistryClass.set("com.example.SampleRegistry")
        prunus.aot.mode.set(mode)
        project.evaluate()

        then:
        taskAbsent.call(project)

        where:
        mode                | taskAbsent
        AotMode.APPLICATION | { p -> p.tasks.withType(AbstractByteBuddyTask).isEmpty() }
        AotMode.LIBRARY     | { p -> p.tasks.findByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK) == null }
    }

    def "aot pipeline tasks execute when aot.enabled is true regardless of mode"() {
        given:
        def project = ProjectBuilder.builder().withName("aot-pipeline-enabled-${mode}").build()
        def plugin = new LibprunusCorePlugin()

        when:
        plugin.apply(project)
        def prunus = (PrunusExtension) project.extensions.getByName("prunus")
        prunus.aot.enabled.set(true)
        prunus.aot.logRegistryClass.set("com.example.SampleRegistry")
        prunus.aot.mode.set(mode)
        project.evaluate()
        Task task = taskLookup.call(project)

        then:
        task.onlyIf.isSatisfiedBy(task)

        where:
        mode                | taskLookup
        AotMode.APPLICATION | { p -> p.tasks.withType(AbstractByteBuddyTask).first() }
        AotMode.LIBRARY     | { p -> p.tasks.getByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK) as GenerateLibraryWhitelistTask }
    }
}
