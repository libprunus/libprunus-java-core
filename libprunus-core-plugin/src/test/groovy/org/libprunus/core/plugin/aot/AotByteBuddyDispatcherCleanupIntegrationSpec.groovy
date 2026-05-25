package org.libprunus.core.plugin.aot

import java.util.concurrent.atomic.AtomicBoolean
import net.bytebuddy.dynamic.ClassFileLocator
import org.libprunus.core.plugin.aot.testutil.CloseFailurePlugin
import org.libprunus.core.plugin.aot.testutil.DispatcherFieldInjector
import spock.lang.Specification

class AotByteBuddyDispatcherCleanupIntegrationSpec extends Specification {

    def "close still closes class file locator when compile context clear throws"() {
        given:
        def dispatcher = new AotByteBuddyDispatcher("", null, [] as File[])
        def closed = new AtomicBoolean(false)
        DispatcherFieldInjector.inject(dispatcher.@context, "matchedPluginMasks", new ThrowOnClearMap<>())
        DispatcherFieldInjector.inject(dispatcher, "pluginClassFileLocator", new TrackingClassFileLocator(closed))

        when:
        dispatcher.close()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "clear-fail"
        closed.get()
    }

    def "close propagates context clear exception and still closes class file locator when plugin IOException was already aggregated"() {
        given:
        def dispatcher = new AotByteBuddyDispatcher("", null, [] as File[])
        def locatorClosed = new AtomicBoolean(false)
        DispatcherFieldInjector.inject(dispatcher, "plugins", [new AotByteBuddyDispatcher.RegisteredPlugin(
            AotDispatcherPluginSlot.LOG, new CloseFailurePlugin(new IOException("plugin-close")))])
        DispatcherFieldInjector.inject(dispatcher.@context, "matchedPluginMasks", new ThrowOnClearMap<>())
        DispatcherFieldInjector.inject(dispatcher, "pluginClassFileLocator", new TrackingClassFileLocator(locatorClosed))

        when:
        dispatcher.close()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "clear-fail"
        locatorClosed.get()
    }

    static class TrackingClassFileLocator implements ClassFileLocator {

        private final AtomicBoolean closed

        TrackingClassFileLocator(AtomicBoolean closed) {
            this.closed = closed
        }

        @Override
        Resolution locate(String name) {
            return new Resolution.Illegal(name)
        }

        @Override
        void close() {
            closed.set(true)
        }
    }

    static class ThrowOnClearMap<K, V> extends LinkedHashMap<K, V> {

        @Override
        void clear() {
            throw new IllegalStateException("clear-fail")
        }
    }
}
