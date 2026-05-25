package org.libprunus.core.log.runtime

import spock.lang.Specification

class ObjectArrayRendererSpec extends Specification {

    private static StringBuilderWithContext contextOf(
            StringBuilder builder,
            int objectStartLength,
            int maxObjectLength) {
        def context = new StringBuilderWithContext(builder)
        int absLimit = maxObjectLength < 0 ? Integer.MAX_VALUE : objectStartLength + maxObjectLength
        context.setMaxMessageLength(absLimit)
        context
    }

    private static String render(Closure<?> call) {
        def builder = new StringBuilder()
        call(builder)
        builder.toString()
    }

    private static Loggable soeLoggable() {
        return { StringBuilderWithContext ctx ->
            throw new StackOverflowError("deep")
        } as Loggable
    }

    def "render produces correct output for various non-empty content sizes with unlimited budget and never trips truncation"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, array)

        then:
        builder.toString() == expected
        !context.isTruncated()

        where:
        array                           | expected
        ["a", "b", "c"] as Object[]     | "[a, b, c]"
        ["x"] as Object[]               | "[x]"
        ["hello", "world"] as Object[]  | "[hello, world]"
    }

    def "render with unlimited budget (-1) never blocks separator regardless of accumulated length"() {
        given:
        Object[] array = (1..30).collect { it.toString() } as Object[]
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, array)

        then:
        builder.toString() == "[" + (1..30).join(", ") + "]"
        !context.isTruncated()
    }

    def "render stops before separator when absoluteCap - builder.length() < 2"() {
        given:
        Object[] array = ["a", "b", "c"]

        when:
        def result = render { b -> ObjectArrayRenderer.INSTANCE.render(contextOf(b, 0, 3), array) }

        then:
        result == "..."
    }

    def "render emits closing bracket for empty Object[] only when budget accommodates it and otherwise drops it via truncation latch"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, budget)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, [] as Object[])

        then:
        builder.toString() == expected
        context.isTruncated() == truncated

        where:
        budget || expected || truncated
        -1     || "[]"     || false
        2      || "[]"     || false
        1      || "."      || true
        0      || ""       || true
    }

    def "render respects maxObjectLength covering all boundary paths for a single-element array"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, budget)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, ["A"] as Object[])

        then:
        builder.toString() == expected
        context.isTruncated() == truncated

        where:
        budget || expected || truncated
        -1     || "[A]"    || false
        0      || ""       || true
        1      || "."      || true
        2      || ".."     || true
        3      || "[A]"    || false
    }

    def "render sweeps budget 0..6 for a two-element array covering truncation markers, separator-committed prefix, and full output"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, budget)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, ["A", "B"] as Object[])

        then:
        builder.toString() == expected
        context.isTruncated() == truncated
        builder.toString().contains("B") == containsSecondElement

        where:
        budget || expected   || truncated || containsSecondElement
        -1     || "[A, B]"   || false     || true
        0      || ""         || true      || false
        1      || "."        || true      || false
        2      || ".."       || true      || false
        3      || "..."      || true      || false
        4      || "[..."     || true      || false
        5      || "[A..."    || true      || false
        6      || "[A, B]"   || false     || true
    }

    def "render exits try block via element[0] truncation when budget admits opening bracket but not the first element and still closes with truncation marker"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, 5)
        int depthBefore = context.@renderDepth

        when: "budget=5 lets '[' land (length=1) but 'AAAAA' overflows the remaining 4 chars and triggers value-level truncation"
        ObjectArrayRenderer.INSTANCE.render(context, ["AAAAA"] as Object[])

        then: "appendObjectTo returned false from element[0]; the for-loop is skipped; one prefix char then '...' suffix"
        builder.toString() == "[A..."
        context.isTruncated()
        !builder.toString().contains("AAAAA")
        !builder.toString().endsWith("]")

        and: "finally ran exitRenderDepth so renderDepth is back to baseline (no leak)"
        context.@renderDepth == depthBefore
    }

    def "render at MAX_RENDER_DEPTH writes only opening bracket, emits MAX_DEPTH marker, latches truncation, and never decrements renderDepth"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }

        when:
        ObjectArrayRenderer.INSTANCE.render(context, ["payload-element"] as Object[])

        then: "opening bracket lands and MAX_DEPTH marker is appended via the enterRenderDepth early-return path"
        builder.toString() == "[...[MAX_DEPTH]"
        context.isTruncated()
        !builder.toString().contains("payload-element")

        and: "renderDepth was not decremented by a stray finally — the boundary still rejects a further enter"
        !context.enterRenderDepth()
    }

    def "render with objectStartLength set to prefix length correctly shares budget from the prefix start position"() {
        given:
        def builder = new StringBuilder("prefix")
        def context = contextOf(builder, 6, 4)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, ["A"] as Object[])

        then:
        builder.toString() == "prefix[A]"
        !context.isTruncated()
    }

    def "render represents null array elements as the literal string 'null'"() {
        given:
        def array = [null, "B"] as Object[]
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, array)

        then:
        builder.toString() == "[null, B]"
        !context.isTruncated()
    }

    def "render renders nested arrays normally without depth limit"() {
        given:
        def inner = ["inner"] as Object[]
        def outer = [inner] as Object[]
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, outer)

        then:
        builder.toString() == "[[inner]]"
        !context.isTruncated()
    }

    def "render finally block invokes exitRenderDepth after try-block exception so subsequent enter calls observe the pre-call depth"() {
        given: "exhaust depth budget to one below MAX_RENDER_DEPTH so a single enter still succeeds"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(200)
        (StringBuilderWithContext.MAX_RENDER_DEPTH - 1).times { context.enterRenderDepth() }

        when: "null array triggers NPE inside try; catch absorbs it and finally must restore depth before returning"
        ObjectArrayRenderer.INSTANCE.render(context, null)

        then: "NPE was absorbed and the fallback marker is written"
        noExceptionThrown()
        builder.toString().contains("...[java.lang.NullPointerException]")

        and: "depth was restored to MAX_RENDER_DEPTH - 1; the next enter is still accepted before the boundary fires"
        context.enterRenderDepth()
        !context.enterRenderDepth()
    }

    def "render writes fallback marker and latches truncation correctly for try-block negative paths covering null array, SOE element, and RuntimeException element"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, 100)

        when:
        ObjectArrayRenderer.INSTANCE.render(context, payload)

        then:
        noExceptionThrown()
        builder.toString().contains(markerSubstring)
        context.isTruncated() == expectedTruncated
        mustContainAll.every { builder.toString().contains(it) }
        mustNotContainAny.every { !builder.toString().contains(it) }

        where:
        payload                                                                                                              || markerSubstring                       || expectedTruncated || mustContainAll                                                       || mustNotContainAny
        null                                                                                                                 || "[...[java.lang.NullPointerException]" || false             || ["[...[java.lang.NullPointerException]"]                             || []
        [soeLoggable(), "tail"] as Object[]                                                                                  || "...[SOE]"                            || true              || []                                                                   || ["tail"]
        ["First", soeLoggable(), "Third"] as Object[]                                                                        || "...[SOE]"                            || true              || ["First"]                                                            || ["Third"]
        ["First", { StringBuilderWithContext ctx -> throw new RuntimeException("boom") } as Loggable, "Third"] as Object[]   || "...[java.lang.RuntimeException]"     || false             || ["[First, ...[java.lang.RuntimeException], Third]"]                  || []
    }

    def "render rethrows non-StackOverflowError Error subclasses thrown by an element and still decrements renderDepth via finally so the depth counter is not leaked"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, 100)
        int depthBefore = context.@renderDepth
        Loggable crashLoggable = { StringBuilderWithContext ctx ->
            throw errorToThrow
        } as Loggable
        def array = ["pre", crashLoggable] as Object[]

        when:
        ObjectArrayRenderer.INSTANCE.render(context, array)

        then:
        def ex = thrown(expectedClass)
        ex.message == expectedMessage
        builder.toString() == "[pre, "
        !builder.toString().contains("...")
        !builder.toString().endsWith("]")

        and: "finally ran exitRenderDepth before the Error propagated; the counter is restored to its pre-call value"
        context.@renderDepth == depthBefore

        where:
        errorToThrow                       || expectedClass    | expectedMessage
        new OutOfMemoryError("oom")        || OutOfMemoryError | "oom"
        new InternalError("fatal")         || InternalError    | "fatal"
    }

    def "render absorbs ClassCastException for any non-Object[] payload as fallback marker after writing the opening bracket"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, 100)
        int depthBefore = context.@renderDepth

        when:
        ObjectArrayRenderer.INSTANCE.render(context, invalidValue)

        then: "the cast moved inside try; CCE is bucketed by catch(Throwable) and recorded as fallback marker"
        noExceptionThrown()
        builder.toString().startsWith("[")
        builder.toString().contains("[java.lang.ClassCastException]")

        and: "the finally branch ran exitRenderDepth so renderDepth is back to baseline (no leak)"
        context.@renderDepth == depthBefore

        where:
        invalidValue << [
            "A Simple String",
            1024,
            new Object(),
            Collections.emptyList(),
            new int[]{1, 2, 3},
            new long[]{1L},
            new byte[]{(byte) 1},
            new char[]{'a'},
            new boolean[]{true},
            new short[]{(short) 1},
            new float[]{1.0f},
            new double[]{1.0d}
        ]
    }

}
