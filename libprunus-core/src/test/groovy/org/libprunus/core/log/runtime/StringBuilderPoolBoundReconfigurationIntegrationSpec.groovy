package org.libprunus.core.log.runtime

import spock.lang.Specification

class StringBuilderPoolBoundReconfigurationIntegrationSpec extends Specification {

    def "a builder whose capacity was admitted under the previous bound is rejected after the global bound is lowered such that the dynamic threshold shrinks below its capacity"() {
        given: "set bound=5000 (dynamic threshold=10000), pool a context with capacity 9500 (admitted)"
        def originalBound = LogRuntime.@boundMaxMessageLength
        LogRuntime.@boundMaxMessageLength = 5000
        StringBuilderPool.@POOL.remove()
        def ctx = StringBuilderPool.acquire()
        ctx.builder.ensureCapacity(9500)
        StringBuilderPool.release(ctx)
        def reacq1 = StringBuilderPool.acquire()

        and: "confirm the precondition: the previously-released ctx is indeed pooled"
        assert reacq1.is(ctx)

        when: "lower the bound to 1000 (dynamic threshold collapses to max(8192, 2000)=8192) and re-release"
        LogRuntime.@boundMaxMessageLength = 1000
        StringBuilderPool.release(reacq1)
        def reacq2 = StringBuilderPool.acquire()

        then: "9500 > 8192 after the bound change — the previously-pooled instance is now rejected and a fresh one is returned"
        !reacq2.is(ctx)

        and: "the fresh allocation has the documented INITIAL_CAPACITY"
        reacq2.builder.capacity() == 512

        cleanup:
        StringBuilderPool.release(reacq2)
        StringBuilderPool.@POOL.remove()
        LogRuntime.@boundMaxMessageLength = originalBound
    }
}
