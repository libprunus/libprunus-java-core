package org.libprunus.core.plugin.aot.task

import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import org.libprunus.core.plugin.aot.log.RuntimeBindingCallsiteGenerator
import spock.lang.Specification
import spock.lang.TempDir

class GenerateAotBindingTaskSpec extends Specification {

    @TempDir
    File tempDir

    def "runtimeClasspathCacheInputs short-circuits to empty list for explicit binding and consults whitelist contributors otherwise"() {
        given:
        def outputDir = new File(tempDir, "cache-output-${expectedEmpty}-${Math.abs(explicit.hashCode())}")
        def classesDir = new File(tempDir, "cache-classes-${expectedEmpty}-${Math.abs(explicit.hashCode())}")
        classesDir.mkdirs()
        new File(classesDir, "Dummy.class").bytes = new byte[0]
        def runtimeDirWithWhitelist = new File(tempDir, "runtime-wl-${expectedEmpty}-${Math.abs(explicit.hashCode())}")
        def whitelistFile = new File(runtimeDirWithWhitelist, PrunusPluginConstants.WHITELIST_RESOURCE_PATH)
        whitelistFile.parentFile.mkdirs()
        whitelistFile.text = "sample.Type\n"
        def task = buildTask(
                "gen-cache-${expectedEmpty}-${Math.abs(explicit.hashCode())}",
                "aabbcc",
                explicit,
                classesDir,
                outputDir)
        task.runtimeClasspath.from(runtimeDirWithWhitelist)

        when:
        def inputs = task.runtimeClasspathCacheInputs

        then:
        inputs.isEmpty() == expectedEmpty
        inputs.contains(runtimeDirWithWhitelist.canonicalFile) == !expectedEmpty

        where:
        explicit                     || expectedEmpty
        "org.example.MyBinding"      || true
        ""                           || false
        "   "                        || false
        "\t"                         || false
    }

    def "generate with explicit binding class writes only callsite class file and skips binding class generation"() {
        given:
        def outputDir = new File(tempDir, "output")
        def classesDir = new File(tempDir, "classes")
        def dummyClass = new File(classesDir, "Dummy.class")
        classesDir.mkdirs()
        dummyClass.bytes = new byte[0]

        def bindingId = "aabbcc"
        def explicitClass = "org.example.MyCustomBinding"
        def task = buildTask("gen-explicit", bindingId, explicitClass, classesDir, outputDir)

        when:
        task.generate()

        then:
        def callsiteName = RuntimeBindingCallsiteGenerator.callsiteClassName(bindingId)
        new File(outputDir, callsiteName.replace('.', '/') + ".class").exists()

        and:
        def defaultBindingName = BindingClassSelector.defaultBindingClassName(bindingId)
        !new File(outputDir, defaultBindingName.replace('.', '/') + ".class").exists()

        and:
        def spiFile = new File(outputDir, "META-INF/services/org.libprunus.core.log.runtime.AbstractLogConfig")
        spiFile.exists()
        spiFile.text.trim() == explicitClass
        spiFile.text.endsWith("\n")

        and:
        def pointerFile = new File(outputDir, "META-INF/prunus/aot/runtime-binding-callsite")
        pointerFile.exists()
        pointerFile.text.trim() == callsiteName
        pointerFile.text.endsWith("\n")
    }

    def "generate with explicit binding class skips runtime classpath aggregation"() {
        given:
        def outputDir = new File(tempDir, "output-explicit-skip")
        def classesDir = new File(tempDir, "classes-explicit-skip")
        classesDir.mkdirs()
        new File(classesDir, "Dummy.class").bytes = new byte[0]
        def runtimeJar = new File(tempDir, "should-not-be-opened.jar")
        runtimeJar.bytes = "not a zip".bytes
        def bindingId = "aabbcc"
        def explicitClass = "org.example.MyCustomBinding"
        def task = buildTask("gen-explicit-skip", bindingId, explicitClass, classesDir, outputDir)
        task.runtimeClasspath.from(runtimeJar)

        when:
        task.generate()

        then:
        noExceptionThrown()

        and:
        new File(outputDir, "META-INF/services/org.libprunus.core.log.runtime.AbstractLogConfig").exists()
        def defaultBindingName = BindingClassSelector.defaultBindingClassName(bindingId)
        !new File(outputDir, defaultBindingName.replace('.', '/') + ".class").exists()
    }

    def "generate with default binding class writes both callsite and binding class files"() {
        given:
        def outputDir = new File(tempDir, "output")
        def classesDir = buildRegistryClassesDir()

        def bindingId = "ddeeff"
        def task = buildTask("gen-default", bindingId, "", classesDir, outputDir)

        when:
        task.generate()

        then:
        def callsiteName = RuntimeBindingCallsiteGenerator.callsiteClassName(bindingId)
        new File(outputDir, callsiteName.replace('.', '/') + ".class").exists()

        and:
        def defaultBindingName = BindingClassSelector.defaultBindingClassName(bindingId)
        new File(outputDir, defaultBindingName.replace('.', '/') + ".class").exists()

        and:
        def spiFile = new File(outputDir, "META-INF/services/org.libprunus.core.log.runtime.AbstractLogConfig")
        spiFile.exists()
        spiFile.text.trim() == defaultBindingName
        spiFile.text.endsWith("\n")

        and:
        def pointerFile2 = new File(outputDir, "META-INF/prunus/aot/runtime-binding-callsite")
        pointerFile2.exists()
        pointerFile2.text.trim() == callsiteName
        pointerFile2.text.endsWith("\n")

        and:
        spiFile.text.readLines().findAll { !it.isBlank() }.size() == 1
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
