package org.libprunus.core.plugin.aot

import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension
import org.libprunus.core.plugin.testutil.FakeShadowPlugin
import org.libprunus.core.plugin.testutil.FakeSpringBootPlugin
import spock.lang.Specification

class AotConfigurerPluginWiringIntegrationSpec extends Specification {

    def "core APPLICATION tasks are resolvable by name immediately after apply without requiring afterEvaluate"() {
        given:
        def project = ProjectBuilder.builder().withName("task-ref-immediate").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)

        when:
        new AotConfigurer(project, aot, javaBuild).apply()

        then:
        project.tasks.named(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK).get() != null
        project.tasks.named(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK).get() != null
        project.tasks.named(PrunusPluginConstants.GENERATE_LIBRARY_WHITELIST_TASK).get() != null
    }

    def "resolveLogConfigProviderConflict is wired into run when application plugin is applied after the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-run-after").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.pluginManager.apply("application")
        project.evaluate()
        def runTask = project.tasks.getByName("run")

        then:
        runTask.taskDependencies.getDependencies(runTask)*.name
                .contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
    }

    def "resolveLogConfigProviderConflict is wired into run when application plugin is applied before the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-run-before").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply("application")
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.evaluate()
        def runTask = project.tasks.getByName("run")

        then:
        runTask.taskDependencies.getDependencies(runTask)*.name
                .contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
    }

    def "resolveLogConfigProviderConflict is wired into bootRun when org.springframework.boot plugin is applied"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-bootrun").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        project.evaluate()
        def bootRunTask = project.tasks.getByName("bootRun")

        then:
        bootRunTask.taskDependencies.getDependencies(bootRunTask)*.name
                .contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
    }

    def "run does not receive resolveLogConfigProviderConflict dependency when application plugin is not applied"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-run-absent").build()
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
        project.tasks.findByName("run") == null
    }

    def "bootJar receives packaging lifecycle dependencies when org.springframework.boot plugin is applied after the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-bootjar-packaging-after").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        project.evaluate()
        def bootJarTask = project.tasks.getByName("bootJar")

        then:
        def dependencies = bootJarTask.taskDependencies.getDependencies(bootJarTask)*.name
        dependencies.contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
        dependencies.contains(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
    }

    def "bootJar receives packaging lifecycle dependencies when org.springframework.boot plugin is applied before the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-bootjar-packaging-before").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.evaluate()
        def bootJarTask = project.tasks.getByName("bootJar")

        then:
        def dependencies = bootJarTask.taskDependencies.getDependencies(bootJarTask)*.name
        dependencies.contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
        dependencies.contains(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
    }

    def "shadowJar receives packaging lifecycle dependencies when shadow plugin is applied after the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-shadowjar-packaging-after").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.pluginManager.apply(FakeShadowPlugin.class)
        project.evaluate()
        def shadowJarTask = project.tasks.getByName("shadowJar")

        then:
        def dependencies = shadowJarTask.taskDependencies.getDependencies(shadowJarTask)*.name
        dependencies.contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
        dependencies.contains(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
    }

    def "shadowJar receives packaging lifecycle dependencies when shadow plugin is applied before the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-shadowjar-packaging-before").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply(FakeShadowPlugin.class)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.evaluate()
        def shadowJarTask = project.tasks.getByName("shadowJar")

        then:
        def dependencies = shadowJarTask.taskDependencies.getDependencies(shadowJarTask)*.name
        dependencies.contains(PrunusPluginConstants.RESOLVE_LOG_CONFIG_PROVIDER_CONFLICT_TASK)
        dependencies.contains(PrunusPluginConstants.GENERATE_AOT_BINDING_TASK)
    }

    def "verifyBootJarProviderBinding is wired into check when org.springframework.boot plugin is applied after the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-boot-after").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        project.evaluate()
        def checkTask = project.tasks.getByName("check")

        then:
        checkTask.taskDependencies.getDependencies(checkTask)*.name
                .contains(PrunusPluginConstants.VERIFY_BOOT_JAR_PROVIDER_BINDING_TASK)
    }

    def "verifyBootJarProviderBinding is wired into check when org.springframework.boot plugin is applied before the configurer"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-boot-before").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        project.pluginManager.apply(FakeSpringBootPlugin.class)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.evaluate()
        def checkTask = project.tasks.getByName("check")

        then:
        checkTask.taskDependencies.getDependencies(checkTask)*.name
                .contains(PrunusPluginConstants.VERIFY_BOOT_JAR_PROVIDER_BINDING_TASK)
    }

    def "verifyShadowJarProviderBinding is wired into check when com.github.johnrengelman.shadow plugin is applied"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-shadow").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.pluginManager.apply(FakeShadowPlugin.class)
        project.evaluate()
        def checkTask = project.tasks.getByName("check")

        then:
        checkTask.taskDependencies.getDependencies(checkTask)*.name
                .contains(PrunusPluginConstants.VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK)
    }

    def "verifyShadowJarProviderBinding is absent and not wired into check when shadow plugin is not applied"() {
        given:
        def project = ProjectBuilder.builder().withName("wiring-shadow-absent").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        aot.mode.set(AotMode.APPLICATION)
        new AotConfigurer(project, aot, javaBuild).apply()

        when:
        project.evaluate()
        def checkTask = project.tasks.getByName("check")

        then:
        project.tasks.findByName(PrunusPluginConstants.VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK) == null
        !checkTask.taskDependencies.getDependencies(checkTask)*.name
                .contains(PrunusPluginConstants.VERIFY_SHADOW_JAR_PROVIDER_BINDING_TASK)
    }
}
