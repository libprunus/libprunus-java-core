package org.libprunus.core.log.runtime

import java.util.concurrent.CopyOnWriteArrayList
import spock.lang.Specification

class StringBuilderPoolSpec extends Specification {

    def "acquire returns a context whose truncated and audit-marker flags are reset to false"() {
        given:
        StringBuilderPool.@POOL.remove()
        def seeded = StringBuilderPool.acquire()
        seeded.forceAppendAuditMarker(StringBuilderWithContext.RENDER_TRUNCATION_MARKER)
        StringBuilderPool.release(seeded)

        when:
        def reacquired = StringBuilderPool.acquire()

        then:
        reacquired.is(seeded)
        !reacquired.isTruncated()
        reacquired.@auditMarkerAppended == false

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
    }

    def "acquire propagates LogRuntime.getGlobalMaxMessageLength into the returned context's maxMessageLength"() {
        when:
        def context = StringBuilderPool.acquire()

        then:
        context.@maxMessageLength == LogRuntime.getGlobalMaxMessageLength()

        cleanup:
        StringBuilderPool.release(context)
    }

    def "acquire on a virtual thread always returns a fresh StringBuilder with INITIAL_CAPACITY"() {
        given:
        def results = new CopyOnWriteArrayList<StringBuilderWithContext>()

        when:
        Thread.ofVirtual().start {
            results.add(StringBuilderPool.acquire())
            results.add(StringBuilderPool.acquire())
        }.join()

        then: "both instances are distinct fresh allocations, never reused from a pool"
        results[0].builder.capacity() == 512
        results[1].builder.capacity() == 512
        !results[0].is(results[1])
    }

    def "acquire on a virtual thread propagates LogRuntime.getGlobalMaxMessageLength into the returned context's maxMessageLength"() {
        given:
        def holder = new StringBuilderWithContext[1]

        when:
        Thread.ofVirtual().start {
            holder[0] = StringBuilderPool.acquire()
        }.join()

        then:
        holder[0].@maxMessageLength == LogRuntime.getGlobalMaxMessageLength()
    }

    def "acquire clears the underlying pool slot to null after popping the cursor"() {
        given:
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        StringBuilderPool.release(context)
        assert StringBuilderPool.@POOL.get().cursor == 1
        assert StringBuilderPool.@POOL.get().items[0].is(context)

        when:
        def reacquired = StringBuilderPool.acquire()

        then:
        reacquired.is(context)
        StringBuilderPool.@POOL.get().cursor == 0
        StringBuilderPool.@POOL.get().items[0] == null

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
    }

    def "acquire after a global bound change writes the new bound into the reused context's maxMessageLength"() {
        given: "reset the pool, set an initial bound, and acquire+release one context to seed the per-thread stack"
        def originalBound = LogRuntime.@boundMaxMessageLength
        LogRuntime.@boundMaxMessageLength = 1024
        StringBuilderPool.@POOL.remove()
        def first = StringBuilderPool.acquire()
        StringBuilderPool.release(first)

        when: "the global bound is changed and the same context is re-acquired from the pool"
        LogRuntime.@boundMaxMessageLength = 4096
        def reacquired = StringBuilderPool.acquire()

        then: "the reused instance is the originally-released one — confirming the test reads from the pool path, not from a fresh allocation"
        reacquired.is(first)

        and: "the maxMessageLength reflects the bound that was active at acquire-time, not the bound that was active at release-time"
        reacquired.@maxMessageLength == 4096

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
        LogRuntime.@boundMaxMessageLength = originalBound
    }

    def "acquireWithPrefix returns a context that starts with the prefix and accepts further appends after it"() {
        when:
        def context = StringBuilderPool.acquireWithPrefix(prefix)
        if (suffix) {
            context.builder.append(suffix)
        }

        then:
        context.builder.toString() == prefix + suffix

        cleanup:
        StringBuilderPool.release(context)

        where:
        prefix                              | suffix
        "|> [ENTER] OC.placeOrder(orderId=" | ""
        ""                                  | ""
        "single"                            | ""
        "|> [ENTER] Test("                  | "extra)"
    }

    def "acquireWithPrefix truncates the prefix to globalMaxMessageLength when the prefix is longer than the configured bound"() {
        given: "lower the global bound to 8 and clear the per-thread pool"
        def originalBound = LogRuntime.@boundMaxMessageLength
        LogRuntime.@boundMaxMessageLength = 8
        StringBuilderPool.@POOL.remove()
        def longPrefix = "0123456789ABCDEF"

        when: "a prefix longer than the bound is supplied"
        def context = StringBuilderPool.acquireWithPrefix(longPrefix)

        then: "the builder respects the strict bound — the project-level truncation contract enforced by StringBuilderWithContext.append never exceeds maxMessageLength"
        context.builder.length() == 8

        and: "the builder ends with the truncation suffix — proving the prefix was actually clipped rather than padded or silently dropped"
        context.builder.toString().endsWith("...")

        and: "the visible prefix is the first 5 chars of the original input — triggerTruncation rewinds to (maxLen - 3 = 5) before appending the '...' suffix"
        context.builder.toString().startsWith(longPrefix.substring(0, 5))

        and: "the context is flagged truncated by the project-level append cap"
        context.isTruncated()

        cleanup:
        StringBuilderPool.@POOL.remove()
        LogRuntime.@boundMaxMessageLength = originalBound
    }

    def "acquireWithPrefix forwards null prefix to the project-level append null normalization"() {
        when:
        def context = StringBuilderPool.acquireWithPrefix(null)

        then:
        context.builder.toString() == "null"
        !context.isTruncated()

        cleanup:
        StringBuilderPool.release(context)
    }

    def "acquireWithPrefix returns a truncated context when globalMaxMessageLength is zero"() {
        given:
        def originalBound = LogRuntime.@boundMaxMessageLength
        LogRuntime.@boundMaxMessageLength = 0
        StringBuilderPool.@POOL.remove()

        when:
        def context = StringBuilderPool.acquireWithPrefix("ignored")

        then:
        context.isTruncated()
        context.builder.length() == 0

        cleanup:
        StringBuilderPool.@POOL.remove()
        LogRuntime.@boundMaxMessageLength = originalBound
    }

    def "release followed by acquire reuses the same pooled object"() {
        given:
        def first = StringBuilderPool.acquire()
        first.builder.append("payload")
        StringBuilderPool.release(first)

        when:
        def second = StringBuilderPool.acquire()

        then:
        second.is(first)
        second.builder.length() == 0

        cleanup:
        StringBuilderPool.release(second)
    }

    def "release replaces oversized backing StringBuilder with initial capacity"() {
        given: "capacity beyond 8192 (DEFAULT_MAX_CAPACITY) prevents pooling"
        def context = StringBuilderPool.acquire()
        context.builder.ensureCapacity(8193)

        when:
        StringBuilderPool.release(context)
        def reacquired = StringBuilderPool.acquire()

        then: "not pooled, so a fresh instance with 512 (INITIAL_CAPACITY) is returned"
        reacquired.builder.capacity() == 512
        reacquired.builder.length() == 0

        cleanup:
        StringBuilderPool.release(reacquired)
    }

    def "release admits a context whose backing capacity sits at-or-below the dynamic threshold max(DEFAULT_MAX_CAPACITY, globalBound*2)"() {
        given:
        def originalBound = LogRuntime.@boundMaxMessageLength
        LogRuntime.@boundMaxMessageLength = bound
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        context.builder.ensureCapacity(extraCapacity)

        when:
        StringBuilderPool.release(context)
        def reacquired = StringBuilderPool.acquire()

        then:
        reacquired.is(context)
        reacquired.builder.length() == 0

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
        LogRuntime.@boundMaxMessageLength = originalBound

        where:
        bound | extraCapacity || activeBranch
        1024  | 8192          || "static-equals"
        5000  | 9000          || "dynamic-below-ceiling"
        5000  | 10000         || "dynamic-equals"
    }

    def "release rejects an instance whose capacity exceeds the dynamic threshold derived from getGlobalMaxMessageLength times two"() {
        given: "set the global bound to 5000 so the dynamic threshold becomes 10000; capacity is one above the threshold"
        def originalBound = LogRuntime.@boundMaxMessageLength
        LogRuntime.@boundMaxMessageLength = 5000
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        context.builder.ensureCapacity(10001)

        when:
        StringBuilderPool.release(context)
        def reacquired = StringBuilderPool.acquire()

        then: "10001 is strictly greater than max(8192, 5000*2)=10000, so the instance is rejected"
        !reacquired.is(context)

        and: "the freshly-allocated replacement returns to INITIAL_CAPACITY (512), confirming the pool slot wasn't filled"
        reacquired.builder.capacity() == 512

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
        LogRuntime.@boundMaxMessageLength = originalBound
    }

    def "release does not reset the builder when the capacity gate rejects the instance"() {
        given: "acquire a context, raise its backing builder past the static capacity limit, and write content"
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        context.builder.ensureCapacity(8193)
        context.builder.append("survives-reject")

        when:
        StringBuilderPool.release(context)

        then: "the capacity-gate rejection short-circuits before reset(0); the rejected instance retains its content"
        context.builder.toString() == "survives-reject"

        and: "the next acquire returns a different instance — proving the rejected one was not pooled"
        def reacquired = StringBuilderPool.acquire()
        !reacquired.is(context)

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
    }

    def "release does not advance the per-thread pool cursor when the capacity gate rejects the instance"() {
        given:
        StringBuilderPool.@POOL.remove()
        def baselineCursor = StringBuilderPool.@POOL.get().cursor
        def context = StringBuilderPool.acquire()
        context.builder.ensureCapacity(8193)

        when:
        StringBuilderPool.release(context)

        then:
        StringBuilderPool.@POOL.get().cursor == baselineCursor
        !StringBuilderPool.@POOL.get().items.any { it.is(context) }

        cleanup:
        StringBuilderPool.@POOL.remove()
    }

    def "release clears the builder before the dedup scan when the dedup branch rejects the instance"() {
        given: "release the same context twice; between releases write content that the second release should clear before being rejected"
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        StringBuilderPool.release(context)
        context.builder.append("written-after-first-release")

        when:
        StringBuilderPool.release(context)

        then: "reset(0) ran before the dedup scan, so the second release wiped the builder even though it was then rejected by the dedup branch"
        context.builder.length() == 0

        cleanup:
        StringBuilderPool.acquire()
        StringBuilderPool.@POOL.remove()
    }

    def "release ignores a return beyond the maximum pool depth"() {
        given: "acquire 9 instances (pool depth limit is 8) so they all come from fresh allocation"
        StringBuilderPool.@POOL.remove()
        def all = (1..9).collect { StringBuilderPool.acquire() }
        def toPool = all[0..<8]
        def overflow = all[8]

        and: "fill pool to exactly 8"
        toPool.each { StringBuilderPool.release(it) }

        when: "release one more instance with content beyond pool capacity"
        overflow.builder.append("overflow_data")
        StringBuilderPool.release(overflow)

        and: "drain the pool"
        def drained = (1..8).collect { StringBuilderPool.acquire() }

        then: "content is cleared unconditionally before the pool-depth guard, and the overflow instance is not in the drained set"
        overflow.builder.length() == 0
        !drained.any { it.is(overflow) }

        cleanup:
        drained.each { StringBuilderPool.release(it) }
    }

    def "release ignores immediate double free of the same context"() {
        given:
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()

        when:
        StringBuilderPool.release(context)
        StringBuilderPool.release(context)

        and:
        def first = StringBuilderPool.acquire()
        def second = StringBuilderPool.acquire()

        then:
        first.is(context)
        !second.is(context)

        cleanup:
        StringBuilderPool.release(first)
        StringBuilderPool.release(second)
        StringBuilderPool.@POOL.remove()
    }

    def "release ignores cross-interleaved double free of the same instance"() {
        given:
        StringBuilderPool.@POOL.remove()
        def sbA = StringBuilderPool.acquire()
        def sbB = StringBuilderPool.acquire()

        when: "A and B are released, then A is released again out of order"
        StringBuilderPool.release(sbA)
        StringBuilderPool.release(sbB)
        StringBuilderPool.release(sbA)

        and: "drain the pool"
        def first = StringBuilderPool.acquire()
        def second = StringBuilderPool.acquire()
        def third = StringBuilderPool.acquire()

        then: "only 2 unique instances were pooled; the third acquired is a fresh allocation"
        [sbA, sbB].any { first.is(it) }
        [sbA, sbB].any { second.is(it) }
        !third.is(sbA) && !third.is(sbB)

        cleanup:
        StringBuilderPool.release(first)
        StringBuilderPool.release(second)
        StringBuilderPool.release(third)
        StringBuilderPool.@POOL.remove()
    }

    def "release ignores a return whose backing builder is already held by a pooled wrapper"() {
        given:
        StringBuilderPool.@POOL.remove()
        def sharedBuilder = new StringBuilder(512)
        def ctx1 = new StringBuilderWithContext(sharedBuilder)
        def ctx2 = new StringBuilderWithContext(sharedBuilder)

        when:
        StringBuilderPool.release(ctx1)
        StringBuilderPool.release(ctx2)

        and:
        def acquired = StringBuilderPool.acquire()

        then: "only ctx1 was pooled; ctx2 was rejected because its backing builder matched ctx1's"
        acquired.is(ctx1)

        cleanup:
        StringBuilderPool.release(acquired)
        StringBuilderPool.@POOL.remove()
    }

    def "release followed by acquire on a virtual thread never reuses the released instance"() {
        given:
        def holder = new StringBuilderWithContext[2]

        when:
        Thread.ofVirtual().start {
            def first = StringBuilderPool.acquire()
            first.builder.append("data")
            StringBuilderPool.release(first)
            holder[0] = first
            holder[1] = StringBuilderPool.acquire()
        }.join()

        then: "the second acquire is a new allocation, not the previously released instance"
        !holder[1].is(holder[0])
        holder[1].builder.length() == 0
    }

    def "release on a virtual thread does not deposit into the platform-thread pool"() {
        given:
        def vtSb = new StringBuilderWithContext(new StringBuilder(512))

        when:
        Thread.ofVirtual().start {
            StringBuilderPool.release(vtSb)
        }.join()
        def platformSb = StringBuilderPool.acquire()

        then: "the platform-thread pool was not contaminated by the virtual thread's release, and the subsequent platform acquire still returns a normally-reset usable instance"
        !platformSb.is(vtSb)
        platformSb.builder.length() == 0

        cleanup:
        StringBuilderPool.release(platformSb)
    }

    def "release on a virtual thread does not reset the content of the released StringBuilder"() {
        given:
        def holder = new StringBuilderWithContext[1]

        when:
        Thread.ofVirtual().start {
            def sb = StringBuilderPool.acquire()
            sb.builder.append("content")
            StringBuilderPool.release(sb)
            holder[0] = sb
        }.join()

        then: "release is a no-op on virtual threads so the content is preserved"
        holder[0].builder.toString() == "content"
    }

    def "release silently ignores null without throwing on both platform and virtual threads"() {
        when:
        runner({ StringBuilderPool.release(null) } as Runnable)

        then:
        noExceptionThrown()

        where:
        runner << [
                { Runnable r -> r.run() },
                { Runnable r -> Thread.ofVirtual().start(r).join() },
        ]
    }

    def "release on a virtual thread short-circuits before touching the capacity gate or the platform-thread pool cursor"() {
        given:
        StringBuilderPool.@POOL.remove()
        def baselineCursor = StringBuilderPool.@POOL.get().cursor
        def oversizedBuilder = new StringBuilder(1 << 20)
        def ctx = new StringBuilderWithContext(oversizedBuilder)

        when:
        Thread.ofVirtual().start { StringBuilderPool.release(ctx) }.join()

        then:
        noExceptionThrown()
        ctx.builder.capacity() == 1 << 20
        StringBuilderPool.@POOL.get().cursor == baselineCursor

        cleanup:
        StringBuilderPool.@POOL.remove()
    }

    def "private constructor throws UnsupportedOperationException"() {
        when:
        new StringBuilderPool()

        then:
        thrown(UnsupportedOperationException)
    }

}
