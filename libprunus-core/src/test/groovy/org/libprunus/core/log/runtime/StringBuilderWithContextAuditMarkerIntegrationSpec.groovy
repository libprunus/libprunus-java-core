package org.libprunus.core.log.runtime

import java.util.ConcurrentModificationException
import spock.lang.Specification

/**
 * End-to-end coverage for the four runtime-emitted audit markers documented in
 * docs/usage/truncation-and-markers.md (the fifth, "...[TRUNCATED])", lives in the AOT plugin
 * module and is locked by AotPojoTransformerIntegrationSpec / InspectBehaviorIntegrationSpec).
 *
 * Each test drives the public StringBuilderWithContext.render(...) facade with a real-world-ish
 * input that exercises one marker path, asserting the exact textual form the user-facing doc
 * promises.
 */
class StringBuilderWithContextAuditMarkerIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "renders '...' value-level marker when a collection element overflows the budget; closing bracket is suppressed by the truncated latch"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(20)

        when:
        context.render(["alpha", "beta", "gammaCharlie"])

        then:
        context.builder.toString() == "[alpha, beta, gam..."
        context.isTruncated()
    }

    def "renders '...[CME]' when a Collection iterator throws ConcurrentModificationException partway through"() {
        given:
        def cmeCollection = new AbstractCollection<String>() {
            @Override int size() { return 2 }
            @Override Iterator<String> iterator() {
                return new Iterator<String>() {
                    int count = 0
                    boolean hasNext() { return count < 2 }
                    String next() {
                        if (count++ == 0) return "alpha"
                        throw new ConcurrentModificationException()
                    }
                }
            }
        }
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(64)

        when:
        context.render(cmeCollection)

        then:
        context.builder.toString() == "[alpha, ...[CME]"
        context.isTruncated()
    }

    def "renders '...[CME]' when a RandomAccess list's get() throws IndexOutOfBoundsException (concurrent shrink path)"() {
        given:
        def shrinkingList = new ArrayList<String>(['alpha', 'beta']) {
            int callCount = 0
            @Override String get(int index) {
                if (callCount++ == 0) return "alpha"
                throw new IndexOutOfBoundsException("simulated concurrent shrink at " + index)
            }
        }
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(64)

        when:
        context.render(shrinkingList)

        then:
        context.builder.toString() == "[alpha, ...[CME]"
        context.isTruncated()
    }

    def "renders '...[MAX_DEPTH]' when a Map exceeds the MAX_RENDER_DEPTH (16) container nesting limit"() {
        given:
        Map<String, Object> innermost = [k: "leaf"]
        Map<String, Object> nest = innermost
        (1..17).each { nest = [k: nest] }
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(256)

        when:
        context.render(nest)

        then:
        context.builder.toString().contains("...[MAX_DEPTH]")
        context.builder.length() <= 256
        context.isTruncated()
    }

    def "Map of Object array of Map nesting emits a single MAX_DEPTH marker at the first frame that exceeds the limit and prevents inner content from leaking"() {
        given: "deep nesting that alternates Map → Object[] → Map until depth exceeds MAX_RENDER_DEPTH"
        Object node = "leaf"
        (1..(StringBuilderWithContext.MAX_RENDER_DEPTH + 2)).each { i ->
            node = (i % 2 == 0) ? ([k: node] as Map<String, Object>) : ([node] as Object[])
        }
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(512)

        when: "the deeply-nested heterogeneous structure is rendered through the public facade"
        context.render([root: node])

        then: "the MAX_DEPTH marker appears in the output"
        def rendered = context.builder.toString()
        rendered.contains("...[MAX_DEPTH]")

        and: "the MAX_DEPTH marker appears exactly once — first-wins audit-marker latch holds across alternating container renderers"
        rendered.findAll(/\.\.\.\[MAX_DEPTH\]/).size() == 1

        and: "the innermost leaf payload did not leak through — the depth guard cut the chain before it reached the leaf"
        !rendered.contains("leaf")

        and: "the context was flagged truncated by the depth guard"
        context.isTruncated()
    }
}
