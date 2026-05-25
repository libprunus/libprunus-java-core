package org.libprunus.core.plugin.aot

import java.util.concurrent.atomic.AtomicInteger
import net.bytebuddy.build.Plugin
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.log.annotation.MethodLoggingProfiles
import org.libprunus.core.plugin.aot.testutil.DispatcherFieldInjector
import spock.lang.Specification
import spock.lang.TempDir

class AotByteBuddyDispatcherSpec extends Specification {

    @TempDir
    File tempDir

    def "constructor accepts null iterable arguments as empty lists"() {
        when:
        def dispatcher = new AotByteBuddyDispatcher("", null, null)

        then:
        dispatcher.@plugins == []
        dispatcher.@pluginClassFileLocator.is(ClassFileLocator.NoOp.INSTANCE)

        cleanup:
        dispatcher.close()
    }

    def "constructor initializes class file locator with existing classes and classpath entries"() {
        given:
        def classesDir = new File(tempDir, "build/classes/java/main")
        def cpDir = new File(tempDir, "libs")
        classesDir.mkdirs()
        cpDir.mkdirs()

        when:
        def dispatcher = new AotByteBuddyDispatcher("", classesDir, [cpDir] as File[])

        then:
        dispatcher.@plugins == []
        dispatcher.@pluginClassFileLocator.class.simpleName == "Compound"

        cleanup:
        dispatcher.close()
    }

    def "package-private constructor accepts DispatcherInputs with blank registry as empty plugins and NoOp locator"() {
        given:
        def inputs = new AotByteBuddyDispatcher.DispatcherInputs("", [], [])

        when:
        def dispatcher = new AotByteBuddyDispatcher(inputs)

        then:
        dispatcher.@plugins == []
        dispatcher.@pluginClassFileLocator.is(ClassFileLocator.NoOp.INSTANCE)

        cleanup:
        dispatcher.close()
    }

    def "matches returns false when registry class is not configured"() {
        given:
        def dispatcher = new AotByteBuddyDispatcher("", null, [] as File[])

        expect:
        !dispatcher.matches(TypeDescription.ForLoadedType.of(type))

        cleanup:
        dispatcher.close()

        where:
        type << [String, Integer, Long, Object]
    }

    def "matches returns false after dispatcher is closed"() {
        given:
        def dispatcher = new AotByteBuddyDispatcher("", null, [] as File[])
        dispatcher.close()

        expect:
        !dispatcher.matches(TypeDescription.ForLoadedType.of(String))
    }

    def "matches returns true for type that the registered plugin accepts"() {
        given:
        def classesDir = new File(TestRegistry.protectionDomain.codeSource.location.toURI())
        def annotationClassesDir = new File(LogRegistry.protectionDomain.codeSource.location.toURI())
        def dispatcher = new AotByteBuddyDispatcher(TestRegistry.name, classesDir, [annotationClassesDir] as File[])

        expect:
        dispatcher.matches(TypeDescription.ForLoadedType.of(DemoService))

        cleanup:
        dispatcher.close()
    }

    def "apply returns original builder when no plugin matches the type"() {
        given:
        def classesDir = new File(TestRegistry.protectionDomain.codeSource.location.toURI())
        def annotationClassesDir = new File(LogRegistry.protectionDomain.codeSource.location.toURI())
        def dispatcher = new AotByteBuddyDispatcher(TestRegistry.name, classesDir, [annotationClassesDir] as File[])
        def builder = Mock(DynamicType.Builder)
        def locator = ClassFileLocator.ForClassLoader.of(TestRegistry.classLoader)

        when:
        def result = dispatcher.apply(builder, TypeDescription.ForLoadedType.of(String), locator)

        then:
        result.is(builder)
        0 * builder._

        cleanup:
        dispatcher.close()
    }

    def "apply returns original builder when dispatcher is closed"() {
        given:
        def dispatcher = new AotByteBuddyDispatcher("", null, [] as File[])
        dispatcher.close()
        def builder = Mock(DynamicType.Builder)
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)

        when:
        def result = dispatcher.apply(builder, TypeDescription.ForLoadedType.of(String), locator)

        then:
        result.is(builder)
    }

    def "apply returns original builder without invoking any plugin when dispatcher is marked closed via state"() {
        given:
        def dispatcher = createDispatcher()
        dispatcher.@closed = true
        def builder = Stub(DynamicType.Builder)
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)

        when:
        def result = dispatcher.apply(builder, TypeDescription.ForLoadedType.of(String), locator)

        then:
        result.is(builder)
    }

    def "apply runs all matching plugins in sequence and returns the final builder"() {
        given:
        def dispatcher = createDispatcher()
        def type = TypeDescription.ForLoadedType.of(DemoService)
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def b0 = Stub(DynamicType.Builder)
        def b1 = Stub(DynamicType.Builder)
        def b2 = Stub(DynamicType.Builder)
        def first = Mock(Plugin)
        def second = Mock(Plugin)
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, first),
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, second)
        ])

        when:
        def result = dispatcher.apply(b0, type, locator)

        then:
        1 * first.matches(type) >> true
        1 * second.matches(type) >> true
        1 * first.apply(b0, type, locator) >> b1
        1 * second.apply(b1, type, locator) >> b2
        result.is(b2)
        0 * _
    }

    def "apply skips plugin application when cached mask does not include plugin slot bit"() {
        given:
        def dispatcher = createDispatcher()
        def type = TypeDescription.ForLoadedType.of(DemoService)
        dispatcher.@context.@matchedPluginMasks.put(type.name, Integer.valueOf(2))
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def builder = Stub(DynamicType.Builder)
        def plugin = Mock(Plugin)
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, plugin)
        ])

        when:
        def result = dispatcher.apply(builder, type, locator)

        then:
        result.is(builder)
        0 * plugin.apply(_, _, _)
        0 * plugin.matches(_)
    }

    def "apply returns original builder immediately when cached mask is zero without invoking plugin"() {
        given:
        def dispatcher = createDispatcher()
        def type = TypeDescription.ForLoadedType.of(DemoService)
        dispatcher.@context.@matchedPluginMasks.put(type.name, Integer.valueOf(0))
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def builder = Stub(DynamicType.Builder)
        def plugin = Mock(Plugin)
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, plugin)
        ])

        when:
        def result = dispatcher.apply(builder, type, locator)

        then:
        result.is(builder)
        0 * plugin.matches(_)
        0 * plugin.apply(_, _, _)
    }

    def "apply uses peekMask fast-path when mask was already cached and does not invoke matches"() {
        given:
        def dispatcher = createDispatcher()
        def type = TypeDescription.ForLoadedType.of(DemoService)
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def builder = Stub(DynamicType.Builder)
        def matchesCallCount = new AtomicInteger(0)
        def applyCallCount = new AtomicInteger(0)
        def plugin = new Plugin() {
            @Override
            boolean matches(TypeDescription target) {
                matchesCallCount.incrementAndGet()
                true
            }

            @Override
            DynamicType.Builder<?> apply(DynamicType.Builder<?> b, TypeDescription t, ClassFileLocator l) {
                applyCallCount.incrementAndGet()
                return b
            }

            @Override
            void close() {}
        }
        DispatcherFieldInjector.inject(dispatcher, "plugins",
            [new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, plugin)])
        dispatcher.@context.@matchedPluginMasks.put(type.name, Integer.valueOf(AotDispatcherPluginSlot.LOG.bitMask()))

        when:
        def result = dispatcher.apply(builder, type, locator)

        then:
        result.is(builder)
        applyCallCount.get() == 1
        matchesCallCount.get() == 0
    }

    def "apply triggers computeMask and writes mask to context cache when peekMask misses"() {
        given:
        def dispatcher = createDispatcher()
        def type = TypeDescription.ForLoadedType.of(DemoService)
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def builder = Stub(DynamicType.Builder)
        def matchesCallCount = new AtomicInteger(0)
        def applyCallCount = new AtomicInteger(0)
        def plugin = new Plugin() {
            @Override
            boolean matches(TypeDescription target) {
                matchesCallCount.incrementAndGet()
                true
            }

            @Override
            DynamicType.Builder<?> apply(DynamicType.Builder<?> b, TypeDescription t, ClassFileLocator l) {
                applyCallCount.incrementAndGet()
                return b
            }

            @Override
            void close() {}
        }
        DispatcherFieldInjector.inject(dispatcher, "plugins",
            [new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, plugin)])

        when:
        def result = dispatcher.apply(builder, type, locator)

        then:
        result.is(builder)
        matchesCallCount.get() == 1
        applyCallCount.get() == 1
        dispatcher.@context.@matchedPluginMasks.get(type.name) == Integer.valueOf(AotDispatcherPluginSlot.LOG.bitMask())
    }

    def "matches populates mask cache so a subsequent matches call does not recompute the mask"() {
        given:
        def dispatcher = createDispatcher()
        def type = TypeDescription.ForLoadedType.of(DemoService)
        def matchesCallCount = new AtomicInteger(0)
        def plugin = new Plugin() {
            @Override
            boolean matches(TypeDescription target) {
                matchesCallCount.incrementAndGet()
                true
            }

            @Override
            DynamicType.Builder<?> apply(DynamicType.Builder<?> b, TypeDescription t, ClassFileLocator l) { b }

            @Override
            void close() {}
        }
        DispatcherFieldInjector.inject(dispatcher, "plugins",
            [new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, plugin)])

        when:
        dispatcher.matches(type)
        dispatcher.matches(type)

        then:
        matchesCallCount.get() == 1
        dispatcher."getOrComputePluginMatchMask"(type) != 0
    }

    def "apply invokes plugin.apply per call even when mask cache is already populated"() {
        given:
        def dispatcher = createDispatcher()
        def type = TypeDescription.ForLoadedType.of(DemoService)
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)
        def builder = Stub(DynamicType.Builder)
        def applyCount = new AtomicInteger(0)
        def plugin = new Plugin() {
            @Override
            boolean matches(TypeDescription target) { true }

            @Override
            DynamicType.Builder<?> apply(DynamicType.Builder<?> b, TypeDescription t, ClassFileLocator l) {
                applyCount.incrementAndGet()
                return b
            }

            @Override
            void close() {}
        }
        DispatcherFieldInjector.inject(dispatcher, "plugins",
            [new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, plugin)])
        dispatcher.matches(type)

        when:
        dispatcher.apply(builder, type, locator)
        dispatcher.apply(builder, type, locator)

        then:
        applyCount.get() == 2
    }

    private static AotByteBuddyDispatcher createDispatcher() {
        return new AotByteBuddyDispatcher("", null, [] as File[])
    }

    @LogRegistry
    @MethodLoggingProfiles([
        @MethodLoggingProfile(includePackages = ["org.libprunus.core.plugin.aot"], includeClassSuffixes = ["Service"])
    ])
    static class TestRegistry {}

    static class DemoService {}
}
