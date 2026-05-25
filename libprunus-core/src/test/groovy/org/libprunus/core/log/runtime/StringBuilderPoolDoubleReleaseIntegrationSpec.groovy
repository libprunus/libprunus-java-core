package org.libprunus.core.log.runtime

import spock.lang.Specification

class StringBuilderPoolDoubleReleaseIntegrationSpec extends Specification {

    def "log runtime path keeps pool uncorrupted when same context is released twice"() {
        given:
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquireWithPrefix("owner=")

        when: "the runtime failure path releases once"
        StringBuilderWithContext.handleRenderFailure("sample.Owner.method", context, new RuntimeException("render-failed"))

        and: "an outer finally path releases the same instance again"
        StringBuilderPool.release(context)

        and: "the thread drains the pool after the double release"
        def acquired = (1..StringBuilderPool.MAX_POOL_DEPTH).collect { StringBuilderPool.acquire() }

        then: "pool integrity is preserved and duplicate references are not returned"
        acquired.toSet().size() == acquired.size()

        cleanup:
        acquired.each { StringBuilderPool.release(it) }
        StringBuilderPool.@POOL.remove()
    }

    def "cross-interleaved double release through runtime path does not yield the same instance twice on re-acquire"() {
        given:
        StringBuilderPool.@POOL.remove()
        def contextA = StringBuilderPool.acquireWithPrefix("a=")
        def contextB = StringBuilderPool.acquireWithPrefix("b=")

        when: "A is released via the render failure path, B is released normally, then A is released again"
        StringBuilderWithContext.handleRenderFailure("Owner.methodA", contextA, new RuntimeException("render-failed-a"))
        StringBuilderPool.release(contextB)
        StringBuilderPool.release(contextA)

        and: "three subsequent acquires drain the pool and then allocate fresh"
        def first = StringBuilderPool.acquire()
        def second = StringBuilderPool.acquire()
        def third = StringBuilderPool.acquire()

        then: "no two acquired instances are the same physical object"
        !first.is(second)
        !first.is(third)
        !second.is(third)

        cleanup:
        StringBuilderPool.release(first)
        StringBuilderPool.release(second)
        StringBuilderPool.release(third)
        StringBuilderPool.@POOL.remove()
    }

    def "toString fallback path keeps pool uncorrupted when same context is released twice"() {
        given:
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquireWithPrefix("owner=")

        when: "the toString fallback path releases once"
        StringBuilderWithContext.recoverToStringFallback("sample.Owner.toString", context, new RuntimeException("toString-failed"))

        and: "an outer finally path releases the same instance again"
        StringBuilderPool.release(context)

        and: "the thread drains the pool after the double release"
        def acquired = (1..StringBuilderPool.MAX_POOL_DEPTH).collect { StringBuilderPool.acquire() }

        then: "the released context is reused exactly once and no duplicate references leaked into the drain"
        acquired.count { it.is(context) } == 1
        acquired.toSet().size() == acquired.size()

        cleanup:
        acquired.each { StringBuilderPool.release(it) }
        StringBuilderPool.@POOL.remove()
    }

    def "cross-path double release via handleRenderFailure then recoverToStringFallback on the same context is absorbed"() {
        given:
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquireWithPrefix("owner=")

        when: "the render failure path releases first"
        StringBuilderWithContext.handleRenderFailure("Owner.render", context, new RuntimeException("render-failed"))

        and: "the toString fallback path releases the same context again"
        StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, new RuntimeException("toString-failed"))

        and: "two subsequent acquires drain the deduped pool and then allocate fresh"
        def first = StringBuilderPool.acquire()
        def second = StringBuilderPool.acquire()

        then: "the first acquire reuses the deduped context; the second is a fresh allocation"
        first.is(context)
        !second.is(context)

        cleanup:
        StringBuilderPool.release(first)
        StringBuilderPool.release(second)
        StringBuilderPool.@POOL.remove()
    }
}
