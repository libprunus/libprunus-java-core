package org.libprunus.core.plugin.aot

import net.bytebuddy.build.gradle.AbstractByteBuddyTask
import net.bytebuddy.build.gradle.ByteBuddyTaskExtension
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension
import spock.lang.Specification
import spock.lang.TempDir

class AotConfigurerByteBuddyIntegrationSpec extends Specification {

    @TempDir
    File tempDir

    def "testByteBuddy task is not created when configureByteBuddy is called"() {
        given:
        def project = ProjectBuilder.builder().withName("cb-no-test-bytebuddy").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def configurer = new AotConfigurer(project, aot, javaBuild)

        when:
        configurer.configureByteBuddy()
        project.evaluate()

        then:
        project.tasks.withType(AbstractByteBuddyTask).size() == 1
        project.tasks.withType(AbstractByteBuddyTask).first().name == "byteBuddy"
        project.tasks.findByName("testByteBuddy") == null
    }

    def "byteBuddy task onlyIf evaluates to false when aot enabled is not explicitly set"() {
        given:
        def project = ProjectBuilder.builder().withName("cb-onlyif-default").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def aot = project.objects.newInstance(AotExtension)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def configurer = new AotConfigurer(project, aot, javaBuild)

        when:
        configurer.configureByteBuddy()
        project.evaluate()
        def task = project.tasks.withType(AbstractByteBuddyTask).first()

        then:
        !task.onlyIf.isSatisfiedBy(task)
    }

    def "byteBuddy runtimeClasspath reflects mode at evaluation time"() {
        given:
        def project = ProjectBuilder.builder().withName("cb-cp-${mode}").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def dummyJar = new File(tempDir, "dep-${mode}.jar")
        dummyJar.createNewFile()
        project.dependencies.add("runtimeOnly", project.files(dummyJar))
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        aot.mode.set(mode)
        def configurer = new AotConfigurer(project, aot, javaBuild)

        when:
        configurer.configureByteBuddy()
        project.evaluate()
        def task = project.tasks.getByName("byteBuddy") as AbstractByteBuddyTask
        def files = task.inputs.files.files

        then:
        files.any { it.absolutePath == dummyJar.absolutePath } == expectJarPresent

        where:
        mode                || expectJarPresent
        AotMode.APPLICATION || true
        AotMode.LIBRARY     || false
    }

    def "configureByteBuddy resolves runtimeClasspath lazily against mode changes before task query"() {
        given:
        def project = ProjectBuilder.builder().withName("cb-lazy-classpath-mode").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def dummyJar = new File(tempDir, "lazy-dep.jar")
        dummyJar.createNewFile()
        project.dependencies.add("runtimeOnly", project.files(dummyJar))
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        aot.mode.set(AotMode.APPLICATION)
        def configurer = new AotConfigurer(project, aot, javaBuild)

        when:
        configurer.configureByteBuddy()
        aot.mode.set(AotMode.LIBRARY)
        project.evaluate()
        def task = project.tasks.getByName("byteBuddy") as AbstractByteBuddyTask
        def files = task.inputs.files.files

        then:
        !files.any { it.absolutePath == dummyJar.absolutePath }
    }

    def "byteBuddy task exposes AOT input properties from extension and main source set"() {
        given:
        def project = ProjectBuilder.builder().withName("cb-input-property-${propertyKey}").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def aot = project.objects.newInstance(AotExtension)
        aot.enabled.set(true)
        aot.logRegistryClass.set("sample.Registry")
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def configurer = new AotConfigurer(project, aot, javaBuild)

        when:
        configurer.configureByteBuddy()
        project.evaluate()
        def task = project.tasks.getByName("byteBuddy") as AbstractByteBuddyTask
        def actual = task.inputs.properties.get(propertyKey)

        then:
        expectedValueMatcher(actual)

        where:
        propertyKey                                        | expectedValueMatcher
        PrunusPluginConstants.AOT_INPUT_REGISTRY_CLASS     | { it == "sample.Registry" }
        PrunusPluginConstants.AOT_INPUT_CLASSES_OUTPUT_DIR | { it != null && (it as String).replace('\\', '/').contains("classes/java/main") }
    }

    def "configureByteBuddy writes empty registry argument when logRegistryClass is left unset and aot is disabled"() {
        given:
        def project = ProjectBuilder.builder().withName("byte-buddy-empty-registry").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        def configurer = new AotConfigurer(project, aot, javaBuild)

        when:
        configurer.configureByteBuddy()
        project.evaluate()
        def byteBuddy = project.extensions.getByType(ByteBuddyTaskExtension)
        def transformationArguments = byteBuddy.transformations.first().arguments

        then:
        transformationArguments.size() == 1
        transformationArguments[0].value == ""
    }

    def "configureByteBuddy registers a typed transformation with config-time registry argument"() {
        given:
        def project = ProjectBuilder.builder().withName("byte-buddy-configure").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        aot.logRegistryClass.set("com.example.SampleRegistry")
        def configurer = new AotConfigurer(project, aot, javaBuild)

        when:
        configurer.configureByteBuddy()
        project.evaluate()
        def byteBuddy = project.extensions.getByType(ByteBuddyTaskExtension)
        def transformationArguments = byteBuddy.transformations.first().arguments

        then:
        byteBuddy.transformations.size() == 1
        byteBuddy.transformations.first().plugin == AotByteBuddyDispatcher
        transformationArguments.size() == 1
        transformationArguments[0].value == "com.example.SampleRegistry"
    }

    def "configureByteBuddy sets transformation argument from registryClass set after configureByteBuddy call and before project evaluate"() {
        given:
        def project = ProjectBuilder.builder().withName("byte-buddy-late-registry").build()
        project.pluginManager.apply(JavaLibraryPlugin)
        def javaBuild = project.objects.newInstance(JavaBuildExtension)
        def aot = project.objects.newInstance(AotExtension)
        def configurer = new AotConfigurer(project, aot, javaBuild)
        configurer.configureByteBuddy()

        when:
        aot.logRegistryClass.set("com.example.LateRegistry")
        project.evaluate()
        def byteBuddy = project.extensions.getByType(ByteBuddyTaskExtension)
        def transformationArguments = byteBuddy.transformations.first().arguments

        then:
        transformationArguments.size() == 1
        transformationArguments[0].value == "com.example.LateRegistry"
    }
}
