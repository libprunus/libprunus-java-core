package org.libprunus.core.log.runtime

import spock.lang.Specification

class BudgetStateFrameConsistencyIntegrationSpec extends Specification {

    def setupSpec() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "primitive array facades keep truncation result and preserve object budget frame across types"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(5)

        when:
        append.call(context)

        then:
        builder.toString().contains("...")
        context.isTruncated()

        where:
        append << [
                { StringBuilderWithContext c -> c.render([true] as boolean[]) },
                { StringBuilderWithContext c -> c.render([-128] as byte[]) },
                { StringBuilderWithContext c -> c.render([('\uD83D' as char)] as char[]) },
                { StringBuilderWithContext c -> c.render([-1000] as short[]) },
                { StringBuilderWithContext c -> c.render([-1000] as int[]) },
                { StringBuilderWithContext c -> c.render([Long.MIN_VALUE] as long[]) },
                { StringBuilderWithContext c -> c.render([-1.0f] as float[]) },
                { StringBuilderWithContext c -> c.render([-1.0d] as double[]) },
        ]
    }

    def "primitive array element overflow always produces visible truncation marker rather than silent omission"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(budget)

        when:
        render.call(context)

        then:
        builder.toString().contains("..")

        where:
        budget | render
        5      | { StringBuilderWithContext c -> c.render([-32768] as short[]) }
        5      | { StringBuilderWithContext c -> c.render([-1000] as int[]) }
        5      | { StringBuilderWithContext c -> c.render([Long.MIN_VALUE] as long[]) }
        5      | { StringBuilderWithContext c -> c.render([-1.0f] as float[]) }
        5      | { StringBuilderWithContext c -> c.render([-1.0d] as double[]) }
        5      | { StringBuilderWithContext c -> c.render([false] as boolean[]) }
        5      | { StringBuilderWithContext c -> c.render([-128] as byte[]) }
        4      | { StringBuilderWithContext c -> c.render([-1000] as int[]) }
        4      | { StringBuilderWithContext c -> c.render([Long.MIN_VALUE] as long[]) }
    }

    def "primitive array with separator-blocked truncation always produces visible marker rather than silent omission"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(budget)

        when:
        render.call(context)

        then:
        builder.toString().contains("..")

        where:
        budget | render
        4      | { StringBuilderWithContext c -> c.render([1, 2, 3] as int[]) }
        6      | { StringBuilderWithContext c -> c.render([1, 2, 99] as int[]) }
        4      | { StringBuilderWithContext c -> c.render([true, false] as boolean[]) }
    }

    def "appendThrowableFallback keeps fallback format and does not mutate budget state"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(256)

        when:
        context.render(new ThrowingNumber())

        then:
        builder.toString() == "...[" + RuntimeException.name + "]"
        !context.isTruncated()
        builder.length() < 256
    }

    def "primitive array render composes truncation handling and restores the outer budget frame"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(6)

        when:
        context.render([1, 10000] as int[])

        then:
        builder.toString() == "[1,..."
    }

    def "content frame truncation never cannibalizes opening token across array-like renderers"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(4)

        when:
        render.call(context)

        then:
        def result = builder.toString()
        result.startsWith(opening)
        result.contains("..")

        where:
        [render, opening, closing] << [
            [{ StringBuilderWithContext c -> c.render([12345] as int[]) }, "[", "]"],
            [{ StringBuilderWithContext c -> c.render([12345]) }, "[", "]"],
            [{ StringBuilderWithContext c -> c.render([12345] as Object[]) }, "[", "]"],
            [{ StringBuilderWithContext c -> c.render([12345: 1]) }, "{", "}"],
        ]
    }

    def "appendThrowableFallback uses real remaining capacity instead of absolute budget argument"() {
        given:
        def builder = new StringBuilder("x" * 99)
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(100)

        when:
        context.appendThrowableFallback(new RuntimeException("boom"))

        then:
        builder.length() == 100
        builder.toString().endsWith("...")
        !builder.toString().endsWith("]")
    }

    def "non-container throwable fallback uses full maxMessageLength budget in public runtime path"() {
        given:
        def builder = new StringBuilder()

        when:
        new StringBuilderWithContext(builder).tap { setMaxMessageLength(16) }.render(value)

        then:
        builder.toString() == "...[java.lang..."
        builder.length() == 16

        where:
        value << [
            ({ StringBuilderWithContext ctx ->
                throw new RuntimeException("boom")
            } as Loggable),
            new ThrowingNumber()
        ]
    }

    def "appendThrowableFallback routes StackOverflowError through forceAppendAuditMarker and degrades marker to fit budget"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(budget)

        when:
        context.appendThrowableFallback(new StackOverflowError())

        then:
        context.isTruncated()
        builder.toString() == expected

        where:
        budget | expected
        2      | ".."
        4      | "...["
        7      | "...[SOE"
        8      | "...[SOE]"
    }

    def "container opening requires two-byte budget so no dangling opener is emitted"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(1)

        when:
        render.call(context)

        then:
        builder.toString() == "."

        where:
        render << [
                { StringBuilderWithContext c -> c.render([1] as int[]) },
                { StringBuilderWithContext c -> c.render([1]) },
                { StringBuilderWithContext c -> c.render([1] as Object[]) },
                { StringBuilderWithContext c -> c.render([k: 1]) },
        ]
    }

    def "forceAppendAuditMarker replaces tail to fit marker when content fills budget"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder("pre:"))
        context.setMaxMessageLength(10)
        context.append("abcdef")

        when:
        context.forceAppendAuditMarker("[SOE]")

        then:
        context.builder.toString() == "pre:a[SOE]"
    }

    def "render and appendThrowableFallback and forceAppendAuditMarker are no-ops once context is truncated"() {
        given: "a context that has already emitted an audit marker and latched truncated state"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(8)
        context.forceAppendAuditMarker("...[SOE]")
        def frozenOutput = builder.toString()

        when:
        operation.call(context)

        then: "no write occurs and the truncated latch holds"
        builder.toString() == frozenOutput
        context.isTruncated()

        where:
        operation << [
                { StringBuilderWithContext c -> c.render([1] as int[]) },
                { StringBuilderWithContext c -> c.render([1]) },
                { StringBuilderWithContext c -> c.render([k: 1]) },
                { StringBuilderWithContext c -> c.render("text") },
                { StringBuilderWithContext c -> c.appendThrowableFallback(new RuntimeException("boom")) },
                { StringBuilderWithContext c -> c.forceAppendAuditMarker("...[MAX_DEPTH]") },
        ]
    }

    private static final class ThrowingNumber extends Number {

        @Override
        int intValue() {
            return 0
        }

        @Override
        long longValue() {
            return 0L
        }

        @Override
        float floatValue() {
            return 0.0f
        }

        @Override
        double doubleValue() {
            return 0.0d
        }

        @Override
        String toString() {
            throw new RuntimeException("boom")
        }
    }
}
