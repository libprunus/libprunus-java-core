package org.libprunus.core.plugin.aot.task

import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.plugin.aot.BindingIdSanitizer
import spock.lang.Specification
import spock.lang.TempDir

class GenerateAotBindingTaskIntegrationSpec extends Specification {

    @TempDir
    File tempDir

    def "generate does not write callsite or resource files when default binding path is skipped due to empty classes dirs"() {
        given:
        def outputDir = new File(tempDir, "output")
        def missingClassesDir = new File(tempDir, "missing-classes")
        def bindingId = "aabbcc"
        def task = buildTask("gen-skip-empty-classes", bindingId, "", missingClassesDir, outputDir)

        when:
        task.generate()

        then:
        !outputDir.exists()

        and:
        !new File(outputDir, "META-INF/services/org.libprunus.core.log.runtime.AbstractLogConfig").exists()
        !new File(outputDir, "META-INF/prunus/aot/runtime-binding-callsite").exists()
    }

    def "generate writes generated classes and SPI file under sanitized binding id package"() {
        given:
        def outputDir = new File(tempDir, "sanitized-output-${Math.abs(rawId.hashCode())}")
        def classesDir = buildRegistryClassesDir()
        def sanitizedId = BindingIdSanitizer.sanitizeForPackageSegment(rawId)
        def expectedBindingClass = "org.libprunus.aot.generated.${sanitizedId}.LogConfigBindingImpl"
        def task = buildTask("gen-${Math.abs(rawId.hashCode())}", rawId, "", classesDir, outputDir)

        when:
        task.generate()

        then:
        new File(outputDir, "org/libprunus/aot/generated/${sanitizedId}/RuntimeBindingCallsite.class").exists()
        new File(outputDir, "org/libprunus/aot/generated/${sanitizedId}/LogConfigBindingImpl.class").exists()
        new File(outputDir, "META-INF/services/org.libprunus.core.log.runtime.AbstractLogConfig").text.trim() ==
            expectedBindingClass

        where:
        rawId << ["  trim123  ", " 123-core-app ", "int"]
    }

    def "generate with colliding sanitized bases keeps distinct output packages for different binding ids"() {
        given:
        def outputDir = new File(tempDir, "collision-output")
        def classesDir = buildRegistryClassesDir()
        def leftBindingId = "my-variant"
        def rightBindingId = "my.variant"
        def leftSanitized = BindingIdSanitizer.sanitizeForPackageSegment(leftBindingId)
        def rightSanitized = BindingIdSanitizer.sanitizeForPackageSegment(rightBindingId)
        def leftTask = buildTask("gen-collision-left", leftBindingId, "", classesDir, outputDir)
        def rightTask = buildTask("gen-collision-right", rightBindingId, "", classesDir, outputDir)

        when:
        leftTask.generate()
        rightTask.generate()

        then:
        leftSanitized != rightSanitized
        new File(outputDir, "org/libprunus/aot/generated/${leftSanitized}/LogConfigBindingImpl.class").exists()
        new File(outputDir, "org/libprunus/aot/generated/${rightSanitized}/LogConfigBindingImpl.class").exists()
    }

    def "generate fails early when explicit binding class is not a valid Java FQCN"() {
        given:
        def outputDir = new File(tempDir, "invalid-fqcn-output")
        def classesDir = buildRegistryClassesDir()
        def task = buildTask("gen-invalid-fqcn", "aabbcc", "my-invalid-binding.Class", classesDir, outputDir)

        when:
        task.generate()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("not a valid Java FQCN")
        ex.message.contains("my-invalid-binding.Class")

        and:
        !outputDir.exists()
    }

    def "generate fails early when explicit binding class uses reserved namespace"() {
        given:
        def outputDir = new File(tempDir, "reserved-namespace-output")
        def classesDir = buildRegistryClassesDir()
        def task = buildTask("gen-reserved-namespace", "aabbcc", "java.lang.CustomBinding", classesDir, outputDir)

        when:
        task.generate()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("reserved package namespace")
        ex.message.contains("java.lang.CustomBinding")

        and:
        !outputDir.exists()
    }

    private GenerateAotBindingTask buildTask(
            String name, String bindingId, String explicitBinding, File classesDir, File outputDir) {
        def project = ProjectBuilder.builder().withName(name).build()
        project.tasks.register(name, GenerateAotBindingTask) { t ->
            t.registryClass.set(TaskRegistryClass.name)
            t.targetCompatibility.set("21")
            t.mainClassesDirs.from(classesDir)
            t.runtimeClasspath.from([])
            t.bindingId.set(bindingId)
            t.explicitBindingClass.set(explicitBinding)
            t.outputDirectory.set(outputDir)
        }.get() as GenerateAotBindingTask
    }

    private File buildRegistryClassesDir() {
        def dir = new File(tempDir, "registry-classes")
        dir.mkdirs()
        [TaskRegistryClass, LogRegistry].each { clazz ->
            def resourcePath = clazz.name.replace('.', '/') + '.class'
            def bytes = clazz.classLoader.getResourceAsStream(resourcePath).bytes
            def target = new File(dir, resourcePath)
            target.parentFile.mkdirs()
            target.bytes = bytes
        }
        dir
    }

    @LogRegistry
    static class TaskRegistryClass {}
}
