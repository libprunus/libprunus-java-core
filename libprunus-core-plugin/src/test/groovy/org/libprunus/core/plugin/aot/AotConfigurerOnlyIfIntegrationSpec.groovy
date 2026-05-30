package org.libprunus.core.plugin.aot

import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.aot.task.GenerateAotBindingTask
import org.libprunus.core.plugin.aot.task.GenerateLibraryWhitelistTask
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension
import spock.lang.Specification

class AotConfigurerOnlyIfIntegrationSpec extends Specification {

    def "AOT action task is not registered when aot enabled is false even when the mode matches"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-disabled-onlyif-${mode}-${taskName}").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(false)
        aot.logRegistryClass.set("sample.demo.Registry")
        aot.mode.set(mode)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        project.tasks.findByName(taskName) == null

        where:
        mode                | taskName
        AotMode.APPLICATION | PrunusPluginConstants.GENERATE_AOT_BINDING_TASK
        AotMode.LIBRARY     | PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK
    }

    def "resolveLogConfigProviderConflict task onlyIf is satisfied when aot enabled is true and APPLICATION mode"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-resolve-task-onlyif-enabled").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def task = project.tasks.getByName(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)

        then:
        task.onlyIf.isSatisfiedBy(task)
    }

    def "registerGenerateAotBindingTask maps targetJavaVersion to targetCompatibility string"() {
        given:
        def project = ProjectBuilder.builder().withName("gab-target-compat").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        javaBuild.targetJavaVersion.set(17)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def task = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) as GenerateAotBindingTask

        then:
        task.targetCompatibility.get() == "17"
    }

    def "registerGenerateAotBindingTask falls back to default targetCompatibility when targetJavaVersion is null"() {
        given:
        def project = ProjectBuilder.builder().withName("gab-target-compat-null").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        javaBuild.targetJavaVersion.set((Integer) null)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def task = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) as GenerateAotBindingTask

        then:
        task.targetCompatibility.get() == "25"
    }

    def "apply falls back to default whitelist targetCompatibility when targetJavaVersion is null in LIBRARY mode"() {
        given:
        def project = ProjectBuilder.builder().withName("gw-target-compat-null").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        javaBuild.targetJavaVersion.set((Integer) null)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.LIBRARY)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def task = project.tasks.getByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK) as GenerateLibraryWhitelistTask

        then:
        task.targetCompatibility.get() == "25"
    }

    def "apply and evaluate succeed when mode property has no value because mode routing is deferred to execution"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-no-mode-value").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("org.example.Registry")
        aot.mode.set(project.objects.property(AotMode))

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def generateTask = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)

        then:
        noExceptionThrown()
        !generateTask.onlyIf.isSatisfiedBy(generateTask)
    }

    def "mode configured after apply and before evaluate controls onlyIf behavior at execution time"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-mode-set-after-apply").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        aot.mode.set(AotMode.LIBRARY)
        project.evaluate()
        def resolveTask = project.tasks.getByName(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
        def generateTask = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
        def whitelistTask = project.tasks.getByName(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK)

        then:
        !resolveTask.onlyIf.isSatisfiedBy(resolveTask)
        !generateTask.onlyIf.isSatisfiedBy(generateTask)
        whitelistTask.onlyIf.isSatisfiedBy(whitelistTask)
    }

    def "AOT task onlyIf is not satisfied when configured mode does not match the task's intended mode"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-mode-mismatch-${mode}-${taskName}").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(mode)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def task = project.tasks.getByName(taskName)

        then:
        !task.onlyIf.isSatisfiedBy(task)

        where:
        mode                | taskName
        AotMode.LIBRARY     | PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK
        AotMode.LIBRARY     | PrunusPluginConstants.GENERATE_AOT_BINDING_TASK
        AotMode.APPLICATION | PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK
    }
}
