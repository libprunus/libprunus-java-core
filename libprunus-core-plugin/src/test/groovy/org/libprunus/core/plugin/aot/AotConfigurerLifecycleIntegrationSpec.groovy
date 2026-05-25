package org.libprunus.core.plugin.aot

import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.aot.task.GenerateAotBindingTask
import org.libprunus.core.plugin.aot.task.ResolveLogConfigProviderConflictTask
import org.libprunus.core.plugin.aot.task.VerifyPackagedProviderBindingTask
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension
import org.libprunus.core.plugin.testutil.FakeShadowPlugin
import org.libprunus.core.plugin.testutil.FakeSpringBootPlugin
import spock.lang.Specification

class AotConfigurerLifecycleIntegrationSpec extends Specification {

    def "provider conflict task is wired into check lifecycle in APPLICATION mode"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-provider-conflict-check-wiring").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def checkTask = project.tasks.getByName("check")

        then:
        checkTask.taskDependencies.getDependencies(checkTask).any {
            it.name == PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK
        }
    }

    def "provider conflict task is added as dependency on run and bootRun tasks when corresponding plugins are applied"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-provider-conflict-run-wiring").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply("application")
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def runTask = project.tasks.getByName("run")
        def bootRunTask = project.tasks.getByName("bootRun")

        then:
        runTask.taskDependencies.getDependencies(runTask).any {
            it.name == PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK
        }
        bootRunTask.taskDependencies.getDependencies(bootRunTask).any {
            it.name == PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK
        }
    }

    def "generateLibraryWhitelist output is added to jar task via from in LIBRARY mode"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-whitelist-jar-from").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.LIBRARY)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def jarTask = project.tasks.getByName("jar")

        then:
        jarTask.taskDependencies.getDependencies(jarTask)*.name
                .contains(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK)
    }

    def "processResources does not depend on generateLibraryWhitelistTask in LIBRARY mode"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-whitelist-no-process-resources-dep").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.LIBRARY)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def processResourcesTask = project.tasks.getByName("processResources")

        then:
        !processResourcesTask.taskDependencies.getDependencies(processResourcesTask)*.name
                .contains(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK)
    }

    def "application tasks receive shared stable bindingId from project coordinates and main variant"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-stable-binding-id").build()
        project.group = "org.demo"
        project.version = "1.2.3"
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        project.pluginManager.apply(FakeShadowPlugin.class)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def expected = new BindingIdGenerator()
            .generate(project.group.toString(), project.name.toString(), project.version.toString(), project.path, "main")
        def generateTask = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) as GenerateAotBindingTask
        def resolveTask = project.tasks.getByName(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK) as ResolveLogConfigProviderConflictTask
        def verifyBootTask = project.tasks.getByName(PrunusPluginConstants.VERIFY_BOOT_JAR_PROVIDER_BINDING_TASK) as VerifyPackagedProviderBindingTask
        def verifyShadowTask = project.tasks.getByName(PrunusPluginConstants.VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK) as VerifyPackagedProviderBindingTask

        then:
        generateTask.bindingId.get() == expected
        resolveTask.bindingId.get() == expected
        verifyBootTask.bindingId.get() == expected
        verifyShadowTask.bindingId.get() == expected
    }

    def "application bindingId resolves provider-backed project coordinates by value"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-provider-coordinate-binding-id").build()
        project.group = project.providers.provider { "org.provider" }
        project.version = project.providers.provider { "2.0.0" }
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def expected = new BindingIdGenerator().generate("org.provider", project.name, "2.0.0", project.path, "main")
        def generateTask = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) as GenerateAotBindingTask

        then:
        generateTask.bindingId.get() == expected
    }

    def "mainBindingId reflects group and version set after apply but before task execution because project access is deferred"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-binding-id-after-evaluate").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.afterEvaluate {
            project.group = "org.late.binding"
            project.version = "7.8.9"
        }
        project.evaluate()
        def task = project.tasks.getByName(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK) as GenerateAotBindingTask
        def expected = new BindingIdGenerator().generate("org.late.binding", project.name, "7.8.9", project.path, "main")

        then:
        task.bindingId.get() == expected
    }

    def "generateAotBinding output is registered on main source set output"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-aot-binding-output-registered").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def mainOutput = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension)
                .sourceSets.getByName("main").output

        then:
        mainOutput.dirs.files.any { it.absolutePath.contains("aot-binding") }
    }

    def "packaging tasks depend on provider conflict and aot binding tasks in APPLICATION mode"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-packaging-lifecycle-wiring").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        project.pluginManager.apply(FakeShadowPlugin.class)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def jarTask = project.tasks.getByName("jar")
        def bootJarTask = project.tasks.getByName("bootJar")
        def shadowJarTask = project.tasks.getByName("shadowJar")

        then:
        [jarTask, bootJarTask, shadowJarTask].every { task ->
            def dependencies = task.taskDependencies.getDependencies(task)*.name
            dependencies.contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK) &&
                    dependencies.contains(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
        }
    }

    def "jar task depends on resolve and generate tasks in APPLICATION mode without additional packaging plugins"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-jar-packaging-lifecycle").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()
        def jarTask = project.tasks.getByName("jar")

        then:
        def dependencies = jarTask.taskDependencies.getDependencies(jarTask)*.name
        dependencies.contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
        dependencies.contains(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
    }

    def "packaging verification tasks are not registered when no packaging plugin is applied"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-verify-not-registered-without-plugin").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        project.tasks.findByName(PrunusPluginConstants.VERIFY_BOOT_JAR_PROVIDER_BINDING_TASK) == null
        project.tasks.findByName(PrunusPluginConstants.VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK) == null
    }

    def "packaging verification tasks are registered only when corresponding packaging plugin is applied"() {
        given:
        def project = ProjectBuilder.builder().withName("apply-verify-conditional-registration").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()
        project.evaluate()

        then:
        project.tasks.findByName(PrunusPluginConstants.VERIFY_BOOT_JAR_PROVIDER_BINDING_TASK) != null
        project.tasks.findByName(PrunusPluginConstants.VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK) == null
    }
}
