package org.libprunus.core.log.runtime

import spock.lang.Specification

class LoggableRendererSpec extends Specification {

    private static StringBuilderWithContext contextOf(
            StringBuilder builder,
            int objectStartLength,
            int maxObjectLength) {
        def context = new StringBuilderWithContext(builder)
        int budget = maxObjectLength < 0 ? Integer.MAX_VALUE : objectStartLength + maxObjectLength
        context.setMaxMessageLength(budget)
        context
    }

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "render leaves context renderDepth balanced and not truncated after a successful loggable invocation"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        Loggable loggable = { ctx -> ctx.append("ok") } as Loggable

        when:
        LoggableRenderer.INSTANCE.render(context, loggable)

        then: "the loggable content was appended"
        builder.toString() == "ok"

        and: "the context is not flagged truncated — the happy path did not trip the truncation latch"
        !context.isTruncated()

        and: "renderDepth has been restored to zero — direct field read confirms the finally block ran exitRenderDepth"
        context.@renderDepth == 0
    }

    def "render produces composite output when loggable internally renders a nested non-loggable object alongside ctx.append calls"() {
        given:
        def nested = new Object()
        Loggable loggable = { StringBuilderWithContext ctx ->
            ctx.append("wrapper(")
            ctx.render(nested)
            ctx.append(")")
        } as Loggable
        def builder = new StringBuilder("prefix-")
        def context = contextOf(builder, builder.length(), 96)

        when:
        LoggableRenderer.INSTANCE.render(context, loggable)

        then:
        def result = builder.toString()
        result.startsWith("prefix-wrapper(java.lang.Object@")
        result.endsWith(")")
        !context.isTruncated()

        and: "renderDepth has been restored to zero — direct field read confirms the finally block ran exitRenderDepth on the outer nested-render path"
        context.@renderDepth == 0
    }

    def "render propagates the parent context's remaining budget into a nested loggable invocation causing the nested writer to truncate"() {
        given:
        def nestedTruncated = false
        Loggable nested = { StringBuilderWithContext ctx ->
            ctx.append("ABCDE")
            nestedTruncated = ctx.isTruncated()
        } as Loggable
        Loggable parent = { StringBuilderWithContext ctx ->
            ctx.append("123456")
            ctx.render(nested)
        } as Loggable
        def builder = new StringBuilder("pref-")
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(5 + 8)

        when:
        LoggableRenderer.INSTANCE.render(context, parent)

        then:
        nestedTruncated
        builder.toString() == "pref-12345..."

        and: "renderDepth has been restored to zero — direct field read confirms the finally block ran exitRenderDepth even after the nested-budget truncation latched"
        context.@renderDepth == 0
    }

    def "render emits FQ-class throwable fallback marker appended to any prior writes, leaving truncated false and renderDepth balanced for every non-Error failed invocation path"() {
        given:
        def builder = new StringBuilder(prefix)
        def context = contextOf(builder, 0, 100)

        when:
        LoggableRenderer.INSTANCE.render(context, value)

        then:
        builder.toString() == expectedOutput

        and: "comfortable budget keeps the truncation latch unset after the marker emission"
        !context.isTruncated()

        and: "renderDepth has been restored to zero — direct field read confirms the finally block ran exitRenderDepth"
        context.@renderDepth == 0

        where:
        prefix   | value                                                                                    || expectedOutput
        "prefix" | new Object()                                                                             || "prefix...[java.lang.ClassCastException]"
        "prefix" | ({ ctx -> ctx.builder.append("Trash"); throw new RuntimeException("boom") } as Loggable) || "prefixTrash...[java.lang.RuntimeException]"
        ""       | ({ ctx -> throw new RuntimeException("boom") } as Loggable)                              || "...[java.lang.RuntimeException]"
    }

    def "render rethrows non-StackOverflowError Error subtypes preserving prior writes, leaving truncated false and renderDepth balanced"() {
        given:
        def builder = new StringBuilder(prefix)
        def context = contextOf(builder, 0, 100)
        Loggable loggable = { StringBuilderWithContext ctx ->
            ctx.builder.append(priorWrite)
            throw error
        } as Loggable

        when:
        LoggableRenderer.INSTANCE.render(context, loggable)

        then: "the exact Error subtype propagates with its original message"
        def ex = thrown(exceptionType)
        ex.message == expectedMessage

        and: "the pre-throw write is preserved — Error rethrow does not roll back the builder"
        builder.toString() == expectedBuilderAfter

        and: "the truncated flag is not flipped — the Error rethrow path does not consume the truncation latch"
        !context.isTruncated()

        and: "renderDepth has been restored to zero — direct field read confirms the finally block ran exitRenderDepth"
        context.@renderDepth == 0

        where:
        prefix   | priorWrite | error                       | exceptionType    | expectedMessage || expectedBuilderAfter
        ""       | ""         | new OutOfMemoryError("oom") | OutOfMemoryError | "oom"           || ""
        "prefix" | "Trash"    | new InternalError("fatal")  | InternalError    | "fatal"         || "prefixTrash"
        "prefix" | "partial"  | new LinkageError("link")    | LinkageError     | "link"          || "prefixpartial"
    }

    def "render does not enter renderDepth or mutate builder when context is already truncated"() {
        given: "a context that has already latched truncated via a previous overflow append"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(3)
        context.append("hello")
        assert context.isTruncated()
        def builderSnapshot = builder.toString()
        boolean loggableCalled = false
        Loggable loggable = { ctx -> loggableCalled = true; ctx.append("X") } as Loggable

        when:
        LoggableRenderer.INSTANCE.render(context, loggable)

        then: "the loggable was not invoked — the truncated short-circuit fires before delegation"
        !loggableCalled

        and: "the builder content is unchanged — no append leaked through the short-circuit path"
        builder.toString() == builderSnapshot

        and: "the truncated flag remains set — the short-circuit did not clear state"
        context.isTruncated()

        and: "renderDepth was never incremented for this call — filling MAX_RENDER_DEPTH slots from this baseline still hits the limit exactly once"
        (StringBuilderWithContext.MAX_RENDER_DEPTH).times { context.enterRenderDepth() }
        !context.enterRenderDepth()
    }

    def "render swallows null value without ClassCastException or NullPointerException when context is already truncated"() {
        given: "a context that is already truncated"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(3)
        context.append("hello")
        assert context.isTruncated()
        def builderSnapshot = builder.toString()

        when: "render is invoked with a null value — would CCE/NPE if the cast occurred before the truncated short-circuit"
        LoggableRenderer.INSTANCE.render(context, null)

        then: "no exception propagated — proving the truncated gate is checked before the (Loggable) cast"
        noExceptionThrown()

        and: "the context remains truncated and unchanged"
        context.isTruncated()

        and: "the builder content is byte-identical to the pre-call snapshot — proving the short-circuit returned before the (Loggable) cast on null and before any write"
        builder.toString() == builderSnapshot
    }

    def "render does not invoke loggable and does not exit renderDepth when entry is blocked at MAX_RENDER_DEPTH"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength())
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }
        boolean loggableCalled = false
        Loggable loggable = { ctx -> loggableCalled = true } as Loggable

        when: "render is invoked three times while depth is saturated"
        LoggableRenderer.INSTANCE.render(context, loggable)
        LoggableRenderer.INSTANCE.render(context, loggable)
        LoggableRenderer.INSTANCE.render(context, loggable)

        then: "the loggable was never invoked — depth-gate blocks delegation"
        !loggableCalled

        and: "the context is truncated by forceAppendAuditMarker via the MAX_DEPTH path"
        context.isTruncated()

        and: "the MAX_DEPTH marker text is present in the builder"
        builder.toString().contains(StringBuilderWithContext.MAX_DEPTH_MARKER)

        and: "renderDepth was never decremented by an unbalanced finally — a fresh enterRenderDepth still fails (the post-MAX_DEPTH truncated latch short-circuits the call, but the depth counter underneath is also still at MAX)"
        !context.enterRenderDepth()

        and: "depth count equals MAX_RENDER_DEPTH exactly: read the counter directly to decouple from the Q10 truncated short-circuit"
        context.@renderDepth == StringBuilderWithContext.MAX_RENDER_DEPTH
    }

    def "render emits the budget-correct fallback marker and flips truncated only when the marker exhausts budget across non-Error and SOE failures"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(budget)
        Loggable loggable = { ctx -> throw throwableFactory() } as Loggable

        when:
        LoggableRenderer.INSTANCE.render(context, loggable)

        then: "no exception propagates — both the non-Error catch branch and the SOE branch absorb the throwable through handleRenderError"
        noExceptionThrown()

        and: "the rendered output matches the expected marker form for the given (throwable, budget) cell"
        builder.toString() == expectedOutput

        and: "the truncated flag matches the budget-driven expectation — tight budgets latch the marker, comfortable budgets emit the full FQ-class marker without latching"
        context.isTruncated() == expectedTruncated

        and: "renderDepth has been restored to zero — direct field read confirms the finally block ran exitRenderDepth on every (throwable, budget) cell"
        context.@renderDepth == 0

        where: "non-Error (RuntimeException) vs SOE crossed with tight vs comfortable budget exercises both marker code paths and both directions of the truncation latch; note that the SOE path goes through forceAppendAuditMarker which prefix-truncates the marker text, while the non-Error path overflows through triggerTruncationBase which writes the ellipsis suffix instead"
        throwableFactory                 | budget || expectedOutput                       | expectedTruncated
        ({ -> new RuntimeException("boom") } as Closure) | 4  || "...."                            | true
        ({ -> new RuntimeException("boom") } as Closure) | 32 || "...[java.lang.RuntimeException]" | false
        ({ -> new StackOverflowError() } as Closure)     | 64 || "...[SOE]"                        | true
        ({ -> new StackOverflowError() } as Closure)     | 4  || "...["                            | true
    }

    def "render translates a null value on a fresh context into the NPE fallback marker via handleRenderError"() {
        given: "a fresh, non-truncated context with a comfortable budget — the (Loggable) cast will succeed on null and the dispatch will trip an NPE that is caught by the renderer's catch(Throwable)"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)

        when:
        LoggableRenderer.INSTANCE.render(context, null)

        then: "no exception propagates — the project-layered catch(Throwable) translates the JDK NPE into a fallback marker"
        noExceptionThrown()

        and: "the marker reflects the FQ class name of the captured NPE — this is project-layered fallback, not a JDK-emitted message"
        builder.toString() == "...[java.lang.NullPointerException]"

        and: "the comfortable budget keeps the truncation latch unset after the marker emission"
        !context.isTruncated()

        and: "renderDepth has been restored to zero — direct field read confirms the finally block ran exitRenderDepth on the catch path"
        context.@renderDepth == 0
    }
}
