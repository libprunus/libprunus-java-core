package org.libprunus.core.log.runtime

import spock.lang.Specification

/**
 * End-to-end coverage for the ObjectArrayRenderer dispatch chain through the public
 * {@link StringBuilderWithContext#appendObjectTo(Object)} facade — exercising the
 * {@code RENDERER_CACHE} ClassValue plus heterogeneous element-level dispatch chains that
 * the unit ObjectArrayRendererSpec cannot reach directly.
 */
class ObjectArrayRendererIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "appendObjectTo dispatches Object[] subtypes (String[], Integer[], Object[][], String[][]) to ObjectArrayRenderer through the ClassValue cache"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(Integer.MAX_VALUE)

        when:
        context.appendObjectTo(payload)

        then: "the canonical bracketed output proves the Object[] dispatch arrived at ObjectArrayRenderer through the renderer-cache type lookup"
        context.builder.toString() == expected

        and: "the unlimited budget never tripped truncation"
        !context.isTruncated()

        where:
        payload                                                || expected
        new String[]{"a", "b"}                                 || "[a, b]"
        new Integer[]{1, 2, 3}                                 || "[1, 2, 3]"
        new Object[]{"x", 1, true}                             || "[x, 1, true]"
        new Object[][]{ new Object[]{"i"} }                    || "[[i]]"
        new String[][]{ new String[]{"a"}, new String[]{"b"} } || "[[a], [b]]"
    }

    def "ObjectArrayRenderer correctly delegates to LoggableRenderer, CollectionRenderer, and MapRenderer for heterogeneous elements"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(Integer.MAX_VALUE)
        Loggable customLoggable = { ctx -> ctx.append("LOG") } as Loggable
        Object[] mixed = [
            "plain",
            [1, 2] as Collection,
            [a: 1] as Map,
            customLoggable,
            [3, 4] as Object[]
        ] as Object[]

        when:
        context.appendObjectTo(mixed)

        then: "each element flows through its own renderer — String literal, Collection via CollectionRenderer ('[1, 2]'), Map via MapRenderer ('{a=1}'), Loggable via LoggableRenderer ('LOG'), nested Object[] via this renderer"
        context.builder.toString() == "[plain, [1, 2], {a=1}, LOG, [3, 4]]"

        and: "the unlimited budget never tripped truncation"
        !context.isTruncated()
    }

    def "nested Object[] with a budget that overflows mid-recursion latches truncation, stops further frames from writing, and unwinds renderDepth back to zero"() {
        given: "a small budget that will overflow somewhere inside the inner ObjectArrayRenderer frame"
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(8)
        Object[] nested = [
            ["alpha", "beta", "gamma"] as Object[],
            "tail"
        ] as Object[]

        when:
        context.appendObjectTo(nested)

        then: "truncation latch fires somewhere in the recursive descent"
        context.isTruncated()

        and: "the buffer ends with the value-level '...' cut suffix once the budget is exhausted"
        context.builder.toString().endsWith("...")
        context.builder.length() <= 8

        and: "tail elements that come after the overflow point are never written"
        !context.builder.toString().contains("tail")
        !context.builder.toString().contains("gamma")

        and: "every recursive frame's finally ran exitRenderDepth; the shared counter is back to zero (no leak)"
        context.@renderDepth == 0
    }

    def "nested Object[] absorbs MAX_RENDER_DEPTH boundary and emits exactly one MAX_DEPTH marker via shared context"() {
        given: "a deeply nested Object[] structure that pushes recursion past MAX_RENDER_DEPTH"
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(Integer.MAX_VALUE)
        Object[] deep = [["leaf"] as Object[]] as Object[]
        (StringBuilderWithContext.MAX_RENDER_DEPTH).times {
            deep = [deep] as Object[]
        }

        when:
        context.appendObjectTo(deep)

        then: "no exception leaked out — the depth guard cut the recursion through the shared renderDepth counter"
        noExceptionThrown()

        and: "the MAX_DEPTH marker appears exactly once — first-wins audit-marker latch holds across recursive ObjectArrayRenderer frames"
        def s = context.builder.toString()
        s.count("...[MAX_DEPTH]") == 1

        and: "the innermost leaf payload did not leak through — the guard fired before the deepest frame ran"
        !s.contains("leaf")

        and: "the context is flagged truncated"
        context.isTruncated()
    }
}
