package org.libprunus.core.log.runtime

import org.slf4j.spi.LoggingEventBuilder
import spock.lang.Specification

class StringBuilderWithContextPublicFacadeIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "public facade object and container render methods append SOE marker and swallow abort signal"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        Loggable soeLoggable = { StringBuilderWithContext ignored ->
            throw new StackOverflowError("deep")
        } as Loggable

        when:
        invoker.call(context, soeLoggable)

        then:
        noExceptionThrown()
        builder.toString().contains("...[SOE]")

        where:
        invoker << [
            { StringBuilderWithContext ctx, Loggable value -> ctx.render(value as Object) },
                { StringBuilderWithContext ctx, Loggable value -> ctx.render([value]) },
                { StringBuilderWithContext ctx, Loggable value -> ctx.render([k: value]) },
                { StringBuilderWithContext ctx, Loggable value -> ctx.render([value] as Object[]) },
        ]
    }

    def "appendObjectTo dispatches collection to renderer and absorbs StackOverflowError from iterator mid-traversal without rethrow"() {
        given: "a list whose iterator yields one element then throws SOE, dispatched via the public-facade appendObjectTo entry point (not the renderer directly) so the test pins the SBWC dispatcher → CollectionRenderer → SBWC fallback path"
        def soe = new StackOverflowError("deep recursion")
        def throwingList = new AbstractList<String>() {
            @Override
            String get(int index) { return "item" }
            @Override
            int size() { return 3 }
            @Override
            Iterator<String> iterator() {
                int count = 0
                new Iterator<String>() {
                    @Override
                    boolean hasNext() { return count < 3 }
                    @Override
                    String next() {
                        if (count++ > 0) throw soe
                        return "item"
                    }
                }
            }
        }
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)

        when:
        context.appendObjectTo(throwingList)

        then: "no exception leaks through the dispatcher boundary — appendObjectTo absorbed the SOE via the CollectionRenderer fallback path"
        noExceptionThrown()

        and: "the buffer holds the partial element rendered before the throw, followed by the SOE audit marker, with no trailing closing bracket"
        builder.toString() == "[item, ...[SOE]"
    }

    def "typed render inputs preserve array rendering and null literal semantics"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)

        when:
        invoker.call(context)

        then:
        builder.toString() == expected

        where:
        [invoker, expected] << [
            [{ StringBuilderWithContext ctx -> ctx.render([true, false] as boolean[]) }, "[true, false]"],
            [{ StringBuilderWithContext ctx -> ctx.render([1, 2] as byte[]) }, "[1, 2]"],
            [{ StringBuilderWithContext ctx -> ctx.render(['a', 'b'] as char[]) }, "[a, b]"],
            [{ StringBuilderWithContext ctx -> ctx.render([1, 2] as short[]) }, "[1, 2]"],
            [{ StringBuilderWithContext ctx -> ctx.render([1, 2] as int[]) }, "[1, 2]"],
            [{ StringBuilderWithContext ctx -> ctx.render([1L, 2L] as long[]) }, "[1, 2]"],
            [{ StringBuilderWithContext ctx -> ctx.render([1.5f, 2.5f] as float[]) }, "[1.5, 2.5]"],
            [{ StringBuilderWithContext ctx -> ctx.render([1.5d, 2.5d] as double[]) }, "[1.5, 2.5]"],
            [{ StringBuilderWithContext ctx -> ctx.render(([1, "x"] as Object[])) }, "[1, x]"],
            [{ StringBuilderWithContext ctx -> ctx.render((boolean[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((byte[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((char[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((short[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((int[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((long[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((float[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((double[]) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((Object[]) null) }, "null"],
        ]
    }

    def "public facade render truncates container types of every dispatch path under tight budget"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(6)

        when:
        invoker.call(context)

        then:
        builder.toString() == expected

        where: "each dispatch path (CollectionRenderer, MapRenderer, ObjectArrayRenderer, primitive int[] inline renderer) is exercised under a budget that forces mid-content truncation"
        [invoker, expected] << [
                [{ StringBuilderWithContext ctx -> ctx.render(["abcdefghij", "klmnopqrst"]) }, "[ab..."],
                [{ StringBuilderWithContext ctx -> ctx.render([abcdef: "ghijklmnop"]) }, "{ab..."],
                [{ StringBuilderWithContext ctx -> ctx.render(["abcdefghij", "klmnopqrst"] as Object[]) }, "[ab..."],
                [{ StringBuilderWithContext ctx -> ctx.render([1234567890, 1234567890] as int[]) }, "[..."],
        ]
    }

    def "public facade render keeps a single truncation marker across primitive and structured dispatch paths"() {
        given:
        def builder = new StringBuilder()

        when:
        new StringBuilderWithContext(builder).tap { setMaxMessageLength(16) }.render(value)

        then:
        def rendered = builder.toString()
        rendered.contains("...")
        rendered.findAll(/\.\.\./).size() == 1

        where:
        value                                                      | _
        [1234567890123456789L, 2234567890123456789L] as long[]     | _
        ["1234567890123456789", "2234567890123456789"]             | _
        ["1234567890123456789": "2234567890123456789"]             | _
        ["1234567890123456789", "2234567890123456789"] as Object[] | _
    }

    def "public facade render preserves exact unlimited formatting across boolean, char, and signed integral primitive arrays"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)

        when:
        context.render(value)

        then:
        builder.toString() == expected

        where:
        value                                                  || expected
        [true, false, true] as boolean[]                       || "[true, false, true]"
        [Byte.MIN_VALUE, -1, 0, Byte.MAX_VALUE] as byte[]      || "[-128, -1, 0, 127]"
        ['a', 'b'] as char[]                                   || "[a, b]"
        [('\uD83D' as char)] as char[]                         || "[\\uD83D]"
        [Short.MIN_VALUE, -1, 0, Short.MAX_VALUE] as short[]   || "[-32768, -1, 0, 32767]"
        [Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE] as int[] || "[-2147483648, -1, 0, 2147483647]"
        [Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE] as long[]    || "[-9223372036854775808, -1, 0, 9223372036854775807]"
    }

    def "unified render entry dispatches scalar, list, map, object array, and typed nulls to the correct renderer"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(128)

        when:
        invoker.call(context)

        then:
        builder.toString() == expected

        where:
        [invoker, expected] << [
            [{ StringBuilderWithContext ctx -> ctx.render("v") }, "v"],
            [{ StringBuilderWithContext ctx -> ctx.render([1, 2]) }, "[1, 2]"],
            [{ StringBuilderWithContext ctx -> ctx.render([k: 1]) }, "{k=1}"],
            [{ StringBuilderWithContext ctx -> ctx.render(([1, "x"] as Object[])) }, "[1, x]"],
            [{ StringBuilderWithContext ctx -> ctx.render((Collection<?>) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((Map<?, ?>) null) }, "null"],
            [{ StringBuilderWithContext ctx -> ctx.render((Object[]) null) }, "null"],
        ]
    }

    def "append CharSequence facade appends and truncates consistently"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(5)

        when:
        def appended = context.append(new StringBuilder("abcdef"))

        then:
        !appended
        context.builder.toString() == "ab..."
    }

    def "appendFallbackString delegates to object fallback append behavior"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(16)

        when:
        def appended = context.appendFallbackString(new Object() {
            @Override
            String toString() {
                return "value"
            }
        })

        then:
        appended
        context.builder.toString() == "value"
    }

    def "appendFallbackString absorbs RuntimeException from toString and appends the typed throwable marker"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(64)

        when:
        def appended = context.appendFallbackString(new Object() {
            @Override
            String toString() {
                throw new RuntimeException("boom")
            }
        })

        then:
        !appended
        context.builder.toString() == "...[java.lang.RuntimeException]"
        !context.isTruncated()
    }

    def "appendFallbackString absorbs StackOverflowError from toString and appends the SOE audit marker"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(64)

        when:
        def appended = context.appendFallbackString(new Object() {
            @Override
            String toString() {
                throw new StackOverflowError("deep")
            }
        })

        then:
        !appended
        context.builder.toString() == "...[SOE]"
        context.isTruncated()
    }

    def "appendFallbackString rethrows non-SOE Error from toString without writing any audit marker"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(64)
        def oom = new OutOfMemoryError("heap")

        when:
        context.appendFallbackString(new Object() {
            @Override
            String toString() {
                throw oom
            }
        })

        then:
        def ex = thrown(OutOfMemoryError)
        ex.is(oom)
        context.builder.length() == 0
        !context.isTruncated()
    }

    def "logAndRelease writes builder text to SLF4J event and recycles the context so a re-acquire returns the same instance with cleared state"() {
        given: "a fresh per-thread pool plus a mocked SLF4J LoggingEventBuilder"
        StringBuilderPool.@POOL.remove()
        def captured = []
        def event = Mock(LoggingEventBuilder) {
            log(_ as String) >> { String s -> captured << s }
        }
        def acquired = StringBuilderPool.acquire()
        acquired.builder.append("integration-msg")

        when: "logAndRelease emits the message and returns the context to the pool, then a fresh acquire pulls from the pool"
        acquired.logAndRelease(event)
        def reacquired = StringBuilderPool.acquire()

        then: "the SLF4J event received the exact builder content"
        captured == ["integration-msg"]

        and: "the pool reused the same instance — proving release-and-recycle ran end-to-end"
        reacquired.is(acquired)

        and: "the reused instance has cleared content — proving release ran reset(0) before returning to the pool"
        reacquired.builder.length() == 0

        and: "the truncated flag is also cleared after release-then-acquire"
        !reacquired.isTruncated()

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
    }

    def "public render of a fallback-eligible value followed by markRenderTruncation on a tight-budget context yields the TRUNCATED tail without leaking the full identity hex"() {
        given: "a strict non-whitelist binding so the value routes to IdentityRenderer, plus a context whose budget allows IdentityRenderer to write a partial className then mark the message-level truncation"
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override
            int getMaxMessageLength() { return 64 }

            @Override
            boolean isWhitelisted(Class<?> type) { return false }
        })
        def value = new Object()
        def className = value.getClass().getName()
        def fullIdentitySuffix = "@" + Integer.toHexString(System.identityHashCode(value))
        def fullIdentityPayload = className + fullIdentitySuffix
        def prefix = "(prefix="
        def markerLength = StringBuilderWithContext.RENDER_TRUNCATION_MARKER.length()
        // budget = prefix + marker → markRenderTruncation rewinds to prefix exactly and writes the marker after it
        def budget = prefix.length() + markerLength
        def builder = new StringBuilder(prefix)
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(budget)

        when: "IdentityRenderer writes through StringBuilderWithContext until its budget runs out partway through the className, then the call site marks the message-level truncation marker"
        context.render(value)
        context.markRenderTruncation()

        then: "the context is in truncated state — the renderer signaled truncation while writing the identity bytes"
        context.isTruncated()

        and: "the closing marker landed on the buffer — proving forceAppendAuditMarker still runs after IdentityRenderer's mid-write abort"
        builder.toString().endsWith(StringBuilderWithContext.RENDER_TRUNCATION_MARKER)

        and: "the total length still respects the message-level budget — forceAppendAuditMarker rewinds rather than overflowing"
        builder.length() <= budget

        and: "the prefix written before render is preserved — the truncation rewind targeted only the unfinished payload, not the caller's prior content"
        builder.toString().startsWith(prefix)

        and: "the full identity payload did not leak — the renderer aborted before the hex suffix was fully written"
        !builder.toString().contains(fullIdentityPayload)

        and: "the hex suffix never landed on the buffer at all — IdentityRenderer never reached the '@' write because className was already exhausting the budget"
        !builder.toString().contains(fullIdentitySuffix)
    }

    def "recoverToStringFallback after markRenderTruncation returns the truncated marker string and recycles the pooled context"() {
        given: "a tight-budget pooled context whose render emits an overflow that gets closed by markRenderTruncation"
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 32 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        })
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        int budget = 32
        context.setMaxMessageLength(budget)
        context.render(("longvalue-that-overflows-tight-budget-easily") as Object)
        context.markRenderTruncation()
        assert context.isTruncated()
        assert context.builder.toString().endsWith(StringBuilderWithContext.RENDER_TRUNCATION_MARKER)

        when:
        def rendered = StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, null)

        then: "the returned string is the same content the builder held and respects the strict length bound"
        rendered.endsWith(StringBuilderWithContext.RENDER_TRUNCATION_MARKER)
        rendered.length() <= budget

        and: "the pool reused the same instance with a cleared backing builder — release ran during recoverToStringFallback"
        def reacquired = StringBuilderPool.acquire()
        reacquired.is(context)
        reacquired.builder.length() == 0

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
    }

    def "public facade render dispatches immutable Map.of and Collections.singletonMap to MapRenderer producing canonical brace output"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)

        when:
        context.render(value)

        then: "the public facade resolves the immutable Map subtype through the renderer cache to MapRenderer, emitting canonical brace output"
        builder.toString() == expected

        and: "the comfortable budget did not trip the truncation latch"
        !context.isTruncated()

        where: "immutable Map.of variants and Collections.singletonMap all dispatch to MapRenderer"
        value                                  || expected
        Map.of("k", "v")                       || "{k=v}"
        Map.of()                               || "{}"
        Collections.singletonMap("a", 1)       || "{a=1}"
        Map.copyOf([a: 1])                     || "{a=1}"
    }

}
