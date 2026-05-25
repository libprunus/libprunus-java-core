package org.libprunus.core.plugin.aot.task

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import net.bytebuddy.dynamic.ClassFileLocator
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.Specification
import spock.lang.TempDir

class AbstractAotActionTaskSpec extends Specification {

    @TempDir
    File tempDir

    def "getGeneratorVersion exposes the AOT_GENERATOR_VERSION constant literal as task input fingerprint"() {
        given:
        def task = createTask("gen-version-pin", [buildValidClassesDir("v")])

        when:
        def version = task.generatorVersion

        then:
        version == "1"
        version == PrunusPluginConstants.AOT_GENERATOR_VERSION
    }

    def "executeWithAotContext returns false without invoking the action when every mainClassesDirs entry is missing"() {
        given:
        def missingA = new File(tempDir, "missing-a")
        def missingB = new File(tempDir, "missing-b")
        def task = createTask("missing-dirs", [missingA, missingB])
        def invoked = new AtomicBoolean(false)

        when:
        def returned = task.invokeFlag(invoked)

        then:
        returned == false
        invoked.get() == false
        noExceptionThrown()
    }

    def "executeWithAotContext forwards the registryClass property value verbatim to the action and returns true"() {
        given:
        def captured = new AtomicReference<String>()
        def validDir = buildValidClassesDir("forward-v")
        def project = ProjectBuilder.builder().withName("forward-registry").withProjectDir(tempDir).build()
        def task = project.tasks.register("forward-registry", TestAotTask) { t ->
            t.registryClass.set('com.example.WeirdName$Inner')
            t.targetCompatibility.set("21")
            t.mainClassesDirs.from([validDir])
        }.get() as TestAotTask

        when:
        def returned = task.invokeCapturingRegistry(captured)

        then:
        captured.get() == 'com.example.WeirdName$Inner'
        returned == true
    }

    def "executeWithAotContext propagates locator construction failure without invoking the action"() {
        given:
        def validDir = buildValidClassesDir("locator-construct-fail")
        def project = ProjectBuilder.builder().withName("locator-construct-fail").withProjectDir(tempDir).build()
        def task = project.tasks.register("locator-construct-fail", TestAotTask) { t ->
            t.registryClass.set(TaskRegistryClass.name)
            t.targetCompatibility.set("not-a-number")
            t.mainClassesDirs.from([validDir])
        }.get() as TestAotTask
        def captured = new AtomicReference<String>()

        when:
        task.invokeCapturingRegistry(captured)

        then:
        thrown(RuntimeException)
        captured.get() == null
    }

    def "executeWithAotContext propagates IOException from the action and supplies a non-null locator before failing"() {
        given:
        def validDir = buildValidClassesDir("io-rethrow")
        def task = createTask("io-rethrow", [validDir])
        def boom = new IOException("simulated action failure")
        def invocations = new AtomicInteger(0)
        def observedLocator = new AtomicReference<ClassFileLocator>()

        when:
        task.invokeThrowing(boom, invocations, observedLocator)

        then:
        def ex = thrown(IOException)
        ex.is(boom)
        invocations.get() == 1
        observedLocator.get() != null
    }

    private TestAotTask createTask(String name, List<File> classesDirs) {
        def project = ProjectBuilder.builder().withName(name).withProjectDir(tempDir).build()
        project.tasks.register(name, TestAotTask) { t ->
            t.registryClass.set(TaskRegistryClass.name)
            t.targetCompatibility.set("21")
            t.mainClassesDirs.from(classesDirs)
        }.get() as TestAotTask
    }

    private File buildValidClassesDir(String dirName) {
        def dir = new File(tempDir, dirName)
        dir.mkdirs()
        [TaskRegistryClass, LogRegistry].each { clazz ->
            copyClassBytes(dir, clazz)
        }
        dir
    }

    private static void copyClassBytes(File rootDir, Class<?> clazz) {
        def resourcePath = clazz.name.replace('.', '/') + '.class'
        def stream = clazz.classLoader.getResourceAsStream(resourcePath)
        assert stream != null
        def target = new File(rootDir, resourcePath)
        target.parentFile.mkdirs()
        stream.withCloseable { input ->
            target.withOutputStream { output ->
                output << input
            }
        }
    }

    abstract static class TestAotTask extends AbstractAotActionTask {

        boolean invokeFlag(AtomicBoolean sink) {
            executeWithAotContext([], (String registryClassName, ClassFileLocator locator) -> {
                sink.set(true)
            })
        }

        boolean invokeCapturingRegistry(AtomicReference<String> sink) {
            executeWithAotContext([], (String registryClassName, ClassFileLocator locator) -> {
                sink.set(registryClassName)
            })
        }

        boolean invokeThrowing(IOException error, AtomicInteger counter, AtomicReference<ClassFileLocator> locatorSink) {
            executeWithAotContext([], (String registryClassName, ClassFileLocator locator) -> {
                counter.incrementAndGet()
                locatorSink.set(locator)
                throw error
            })
        }

        @Override
        abstract Property<String> getRegistryClass()

        @Override
        abstract Property<String> getTargetCompatibility()

        @Override
        abstract ConfigurableFileCollection getMainClassesDirs()
    }

    @LogRegistry
    static class TaskRegistryClass {}
}
