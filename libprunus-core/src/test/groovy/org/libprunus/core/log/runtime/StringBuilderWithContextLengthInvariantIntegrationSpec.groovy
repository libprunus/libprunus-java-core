package org.libprunus.core.log.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.slf4j.spi.LoggingEventBuilder
import spock.lang.Specification

class StringBuilderWithContextLengthInvariantIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "across all dispatch paths the rendered SLF4J event payload length never exceeds the configured maxMessageLength"() {
        given: "a mocked SLF4J event capturing the rendered string lengths across heterogeneous dispatch paths"
        def lengths = []
        def event = Mock(LoggingEventBuilder) {
            log(_ as String) >> { String s -> lengths << s.length() }
        }
        int cap = 32
        Object scalar = "scalar-value-that-may-overflow-32-chars"
        Object list = (1..10).collect { it.toString() }
        Object map = (1..10).collectEntries { ["k${it}", it] }
        Object primitiveArray = (1..20).collect { it as int } as int[]
        Object deepNested = [list, map, primitiveArray, "tail"] as Object[]
        StringBuilderPool.@POOL.remove()

        when: "each input is rendered through the full acquire → render → logAndRelease lifecycle"
        [scalar, list, map, primitiveArray, deepNested].each { Object v ->
            def ctx = StringBuilderPool.acquire()
            ctx.setMaxMessageLength(cap)
            ctx.render(v)
            ctx.logAndRelease(event)
        }

        then: "every captured event payload respects the strict length cap — the project-wide invariant promised in the StringBuilderWithContext javadoc"
        lengths.every { it <= cap }

        and: "every input produced exactly one event — proving each iteration actually exercised the dispatch + log path"
        lengths.size() == 5

        cleanup:
        StringBuilderPool.@POOL.remove()
    }

    def "virtual thread acquire-render-logAndRelease lifecycle honors the strict length cap and does not pollute the platform-thread pool cursor"() {
        given: "a mocked SLF4J event capturing the rendered string lengths from inside a virtual thread"
        def lengths = Collections.synchronizedList([])
        def event = Mock(LoggingEventBuilder) {
            log(_ as String) >> { String s -> lengths << s.length() }
        }
        int cap = 24
        StringBuilderPool.@POOL.remove()
        def platformCursorBefore = StringBuilderPool.@POOL.get().cursor
        def latch = new CountDownLatch(1)
        Throwable workerError = null

        when: "the full lifecycle runs entirely on a virtual thread for a heterogeneous set of inputs that all exceed the cap when rendered fully"
        def worker = Thread.ofVirtual().start {
            try {
                [
                        "scalar-value-that-may-overflow-cap-easily",
                        (1..10).collect { it.toString() },
                        (1..10).collectEntries { ["k${it}", it] },
                        (1..20).collect { it as int } as int[],
                        [(1..5).collect { it.toString() }, "tail"] as Object[],
                ].each { Object v ->
                    def ctx = StringBuilderPool.acquire()
                    ctx.setMaxMessageLength(cap)
                    ctx.render(v)
                    ctx.logAndRelease(event)
                }
            } catch (Throwable t) {
                workerError = t
            } finally {
                latch.countDown()
            }
        }

        then: "the worker completed without leaking any exception out of the virtual thread"
        latch.await(5, TimeUnit.SECONDS)
        worker.join()
        workerError == null

        and: "every payload respected the strict length cap"
        lengths.every { it <= cap }

        and: "every input produced exactly one event — proving each iteration actually exercised the dispatch + log path under the virtual thread bypass"
        lengths.size() == 5

        and: "the platform-thread pool cursor was untouched by the virtual thread acquire/release cycle"
        StringBuilderPool.@POOL.get().cursor == platformCursorBefore

        cleanup:
        StringBuilderPool.@POOL.remove()
    }
}
