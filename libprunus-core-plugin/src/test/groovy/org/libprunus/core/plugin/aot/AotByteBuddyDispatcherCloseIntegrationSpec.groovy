package org.libprunus.core.plugin.aot

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.ToIntFunction
import net.bytebuddy.build.Plugin
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.log.annotation.MethodLoggingProfiles
import org.libprunus.core.plugin.aot.testutil.CloseFailurePlugin
import org.libprunus.core.plugin.aot.testutil.DispatcherFieldInjector
import spock.lang.Specification

class AotByteBuddyDispatcherCloseIntegrationSpec extends Specification {

    def "close immediately rethrows RuntimeException from a plugin and aborts the cleanup loop"() {
        given:
        def dispatcher = createDispatcher()
        def locatorClosed = new AtomicInteger(0)
        def first = new CloseFailurePlugin(new IllegalStateException("state"))
        def secondClosedCount = new AtomicInteger(0)
        def second = new SuccessfulPlugin(secondClosedCount)
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, first),
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, second)
        ])
        DispatcherFieldInjector.inject(dispatcher, "pluginClassFileLocator", new CountingClassFileLocator(locatorClosed))

        when:
        dispatcher.close()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "state"
        secondClosedCount.get() == 0
        locatorClosed.get() == 0
        dispatcher.@closed == true
    }

    def "close rethrows sole RuntimeException unwrapped from a plugin"() {
        given:
        def dispatcher = createDispatcher()
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(
                AotDispatcherPluginSlot.LOG,
                new CloseFailurePlugin(new IllegalStateException("sole-runtime")))
        ])

        when:
        dispatcher.close()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "sole-runtime"
        ex.cause == null
        dispatcher.@closed == true
    }

    def "close throws classloader IOException when plugins close cleanly"() {
        given:
        def dispatcher = createDispatcher()
        DispatcherFieldInjector.inject(dispatcher, "plugins", [])
        DispatcherFieldInjector.inject(dispatcher, "pluginClassFileLocator", new CloseFailureClassFileLocator("loader-close"))

        when:
        dispatcher.close()

        then:
        def ex = thrown(IOException)
        ex.message == "loader-close"
        ex.suppressed.length == 0
        dispatcher.@closed == true
    }

    def "close preserves plugin IOException as primary and suppresses classloader IOException"() {
        given:
        def dispatcher = createDispatcher()
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(
                AotDispatcherPluginSlot.LOG,
                new CloseFailurePlugin(new IOException("plugin-close")))
        ])
        DispatcherFieldInjector.inject(dispatcher, "pluginClassFileLocator", new CloseFailureClassFileLocator("loader-close"))

        when:
        dispatcher.close()

        then:
        def ex = thrown(IOException)
        ex.message == "plugin-close"
        ex.suppressed.length == 1
        ex.suppressed[0].message == "loader-close"
        dispatcher.@closed == true
    }

    def "close continues closing remaining plugins after an earlier plugin throws IOException"() {
        given:
        def dispatcher = createDispatcher()
        def secondClosedCount = new AtomicInteger(0)
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(
                AotDispatcherPluginSlot.LOG,
                new CloseFailurePlugin(new IOException("first"))),
            new AotByteBuddyDispatcher.RegisteredPlugin(
                AotDispatcherPluginSlot.LOG,
                new SuccessfulPlugin(secondClosedCount))
        ])

        when:
        dispatcher.close()

        then:
        def ex = thrown(IOException)
        ex.message == "first"
        secondClosedCount.get() == 1
        dispatcher.@closed == true
    }

    def "close aggregates multiple IOExceptions via suppressed"() {
        given:
        def dispatcher = createDispatcher()
        def first = new CloseFailurePlugin(new IOException("first"))
        def second = new CloseFailurePlugin(new IOException("second"))
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, first),
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, second)
        ])

        when:
        dispatcher.close()

        then:
        def ex = thrown(IOException)
        ex.message == "first"
        ex.suppressed.length == 1
        ex.suppressed[0].message == "second"
        dispatcher.@closed == true
    }

    def "close rethrows error from plugin"() {
        given:
        def dispatcher = createDispatcher()
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(
                AotDispatcherPluginSlot.LOG,
                new CloseFailurePlugin(new AssertionError("boom")))
        ])

        when:
        dispatcher.close()

        then:
        def ex = thrown(AssertionError)
        ex.message == "boom"
        dispatcher.@closed == true
    }

    def "close clears compile context caches"() {
        given:
        def dispatcher = createDispatcher()
        def context = dispatcher.@context
        context.computeMaskIfAbsent("sample.Foo", { k -> 99 } as ToIntFunction)
        def loaderCallCount = new AtomicInteger(0)

        when:
        dispatcher.close()
        context.computeMaskIfAbsent("sample.Foo", { k ->
            loaderCallCount.incrementAndGet()
            99
        } as ToIntFunction)

        then:
        loaderCallCount.get() == 1
    }

    def "close propagates Error from a plugin and aborts cleanup before context clear"() {
        given:
        def dispatcher = createDispatcher()
        def context = dispatcher.@context
        context.computeMaskIfAbsent("sample.Bar", { k -> 42 } as ToIntFunction)
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(
                AotDispatcherPluginSlot.LOG, new CloseFailurePlugin(new OutOfMemoryError("oom")))
        ])
        def loaderCallCount = new AtomicInteger(0)

        when:
        dispatcher.close()

        then:
        def err = thrown(OutOfMemoryError)
        err.message == "oom"

        when:
        context.computeMaskIfAbsent("sample.Bar", { k ->
            loaderCallCount.incrementAndGet()
            42
        } as ToIntFunction)

        then:
        loaderCallCount.get() == 0
    }

    def "plugin class file locator is initialized as compound locator when classes output directory is provided"() {
        given:
        def classesDir = new File(TestRegistry.protectionDomain.codeSource.location.toURI())

        when:
        def dispatcher = new AotByteBuddyDispatcher("", classesDir, [] as File[])

        then:
        !(dispatcher.@pluginClassFileLocator.is(ClassFileLocator.NoOp.INSTANCE))

        cleanup:
        dispatcher.close()
    }

    def "close releases the plugin class file locator backed by real classes output directory and clears context cache"() {
        given:
        def classesDir = new File(TestRegistry.protectionDomain.codeSource.location.toURI())
        def dispatcher = new AotByteBuddyDispatcher("", classesDir, [] as File[])
        def context = dispatcher.@context
        context.computeMaskIfAbsent("sample.Probe", { k -> 7 } as ToIntFunction)

        when:
        dispatcher.close()

        then:
        noExceptionThrown()
        dispatcher.@closed == true
        context.@matchedPluginMasks.isEmpty()
    }

    def "close is idempotent and marks dispatcher closed after first invocation"() {
        given:
        def dispatcher = createDispatcher()
        dispatcher.close()

        when:
        dispatcher.close()

        then:
        dispatcher.@closed == true
        noExceptionThrown()
    }

    def "close marks dispatcher closed before cleanup so concurrent matches returns false immediately"() {
        given:
        def dispatcher = createDispatcher()
        def cleanupRunning = new CountDownLatch(1)
        def cleanupMayFinish = new CountDownLatch(1)
        def blockingClosePlugin = new Plugin() {
            boolean matches(TypeDescription t) { false }
            DynamicType.Builder<?> apply(DynamicType.Builder<?> b, TypeDescription t, ClassFileLocator l) { b }
            void close() throws IOException {
                cleanupRunning.countDown()
                cleanupMayFinish.await(5, TimeUnit.SECONDS)
            }
        }
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, blockingClosePlugin)
        ])
        def closeThread = Thread.start { dispatcher.close() }
        cleanupRunning.await(5, TimeUnit.SECONDS)

        when:
        def result = dispatcher.matches(TypeDescription.ForLoadedType.of(String))

        then:
        !result

        cleanup:
        cleanupMayFinish.countDown()
        closeThread.join(5000)
    }

    def "close marks dispatcher closed before cleanup so concurrent apply returns immediately"() {
        given:
        def dispatcher = createDispatcher()
        def cleanupRunning = new CountDownLatch(1)
        def cleanupMayFinish = new CountDownLatch(1)
        def blockingClosePlugin = new Plugin() {
            boolean matches(TypeDescription t) { false }
            DynamicType.Builder<?> apply(DynamicType.Builder<?> b, TypeDescription t, ClassFileLocator l) { b }
            void close() throws IOException {
                cleanupRunning.countDown()
                cleanupMayFinish.await(5, TimeUnit.SECONDS)
            }
        }
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, blockingClosePlugin)
        ])
        def closeThread = Thread.start { dispatcher.close() }
        cleanupRunning.await(5, TimeUnit.SECONDS)
        def builder = Stub(DynamicType.Builder)
        def locator = ClassFileLocator.ForClassLoader.of(getClass().classLoader)

        when:
        def result = dispatcher.apply(builder, TypeDescription.ForLoadedType.of(String), locator)

        then:
        result.is(builder)

        cleanup:
        cleanupMayFinish.countDown()
        closeThread.join(5000)
    }

    def "close executes plugin close before class file locator close"() {
        given:
        def order = Collections.synchronizedList([])
        def dispatcher = createDispatcher()
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(AotDispatcherPluginSlot.LOG, new OrderedPlugin("pluginClose", order))
        ])
        DispatcherFieldInjector.inject(dispatcher, "pluginClassFileLocator", new TrackingClassFileLocator("loaderClose", order))

        when:
        dispatcher.close()

        then:
        order.indexOf("pluginClose") < order.indexOf("loaderClose")
    }

    def "close invokes class file locator close in finally block even when a plugin throws during close"() {
        given:
        def order = Collections.synchronizedList([])
        def dispatcher = createDispatcher()
        DispatcherFieldInjector.inject(dispatcher, "plugins", [
            new AotByteBuddyDispatcher.RegisteredPlugin(
                AotDispatcherPluginSlot.LOG, new CloseFailurePlugin(new IOException("plugin-fail")))
        ])
        DispatcherFieldInjector.inject(dispatcher, "pluginClassFileLocator", new TrackingClassFileLocator("loaderClose", order))

        when:
        try { dispatcher.close() } catch (IOException ignored) {}

        then:
        order.contains("loaderClose")
    }

    def "constructor propagates initialization failure when registry class is missing the LogRegistry annotation"() {
        given:
        def classesDir = new File(TestRegistry.protectionDomain.codeSource.location.toURI())
        def annotationClassesDir = new File(LogRegistry.protectionDomain.codeSource.location.toURI())
        def registryName = NotAnnotatedRegistry.name
        def inputs = new AotByteBuddyDispatcher.DispatcherInputs(
            registryName,
            [classesDir],
            [annotationClassesDir])

        when:
        new AotByteBuddyDispatcher(inputs)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(registryName)
    }

    private static AotByteBuddyDispatcher createDispatcher() {
        return new AotByteBuddyDispatcher("", null, [] as File[])
    }

    @LogRegistry
    @MethodLoggingProfiles([
        @MethodLoggingProfile(includePackages = ["org.libprunus.core.plugin.aot"], includeClassSuffixes = ["Service"])
    ])
    static class TestRegistry {}

    static class NotAnnotatedRegistry {}

    static class SuccessfulPlugin implements Plugin {

        private final AtomicInteger closedCount

        SuccessfulPlugin(AtomicInteger closedCount) {
            this.closedCount = closedCount
        }

        @Override
        boolean matches(TypeDescription target) { false }

        @Override
        DynamicType.Builder<?> apply(
                DynamicType.Builder<?> builder,
                TypeDescription typeDescription,
                ClassFileLocator classFileLocator) {
            return builder
        }

        @Override
        void close() {
            closedCount.incrementAndGet()
        }
    }

    static class CloseFailureClassFileLocator implements ClassFileLocator {

        private final String message

        CloseFailureClassFileLocator(String message) {
            this.message = message
        }

        @Override
        Resolution locate(String name) {
            return new ClassFileLocator.Resolution.Illegal(name)
        }

        @Override
        void close() throws IOException {
            throw new IOException(message)
        }
    }

    static class CountingClassFileLocator implements ClassFileLocator {

        private final AtomicInteger closedCount

        CountingClassFileLocator(AtomicInteger closedCount) {
            this.closedCount = closedCount
        }

        @Override
        Resolution locate(String name) {
            return new ClassFileLocator.Resolution.Illegal(name)
        }

        @Override
        void close() {
            closedCount.incrementAndGet()
        }
    }

    static class OrderedPlugin implements Plugin {

        private final String marker
        private final List<String> order

        OrderedPlugin(String marker, List<String> order) {
            this.marker = marker
            this.order = order
        }

        @Override
        boolean matches(TypeDescription target) { false }

        @Override
        DynamicType.Builder<?> apply(DynamicType.Builder<?> builder, TypeDescription t, ClassFileLocator l) { builder }

        @Override
        void close() {
            order.add(marker)
        }
    }

    static class TrackingClassFileLocator implements ClassFileLocator {

        private final String marker
        private final List<String> order

        TrackingClassFileLocator(String marker, List<String> order) {
            this.marker = marker
            this.order = order
        }

        @Override
        Resolution locate(String name) {
            return new ClassFileLocator.Resolution.Illegal(name)
        }

        @Override
        void close() throws IOException {
            order.add(marker)
        }
    }
}
