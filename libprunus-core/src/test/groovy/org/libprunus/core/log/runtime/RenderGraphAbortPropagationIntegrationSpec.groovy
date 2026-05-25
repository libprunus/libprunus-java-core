package org.libprunus.core.log.runtime

import spock.lang.Specification

class RenderGraphAbortPropagationIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "render swallows SOE at any nesting level and writes abort marker"() {
        given:
        def soeLoggable = ({ StringBuilderWithContext ctx ->
            throw new StackOverflowError("deep")
        } as Loggable)
        def inner = [soeLoggable, "inner-tail"] as Object[]
        def outer = [inner, "outer-tail"] as Object[]

        when:
        def rendered = renderAt(outer)

        then:
        noExceptionThrown()
        rendered.contains("...[SOE]")
        !rendered.contains("inner-tail")
        !rendered.contains("outer-tail")
    }

    def "public renderAt swallows StackOverflowError and emits exact SOE marker across CHAR_SEQUENCE and FALLBACK_TOSTRING dispatch paths [#pathLabel]"() {
        when:
        def rendered = renderAt(valueFactory(), 64)

        then:
        noExceptionThrown()
        rendered == "...[SOE]"

        where:
        pathLabel             | valueFactory
        "CHAR_SEQUENCE"       | { -> soeCharSequence() }
        "FALLBACK_TOSTRING"   | { ->
            new Number() {
                @Override int intValue() { return 0 }

                @Override long longValue() { return 0L }

                @Override float floatValue() { return 0.0f }

                @Override double doubleValue() { return 0.0d }

                @Override String toString() { throw new StackOverflowError("deep") }
            }
        }
    }

    def "nested manual render aborts nested flow and keeps only SOE marker before outer swallow"() {
        given:
        def innerValue = soeCharSequence()
        Loggable loggable = { StringBuilderWithContext ctx ->
            ctx.append("head|")
            ctx.render(innerValue)
            ctx.append("|tail")
        } as Loggable

        when:
        def rendered = renderAt(loggable, 128)

        then:
        noExceptionThrown()
        rendered == "head|...[SOE]"
    }

    def "collection path swallows render graph abort signal and stops outer siblings"() {
        given:
        def throwingMap = new AbstractMap() {
            @Override
            Set entrySet() {
                return new AbstractSet() {
                    @Override
                    Iterator iterator() {
                        return new Iterator() {
                            @Override
                            boolean hasNext() { throw new StackOverflowError("deep") }

                            @Override
                            Object next() { return null }
                        }
                    }

                    @Override
                    int size() { return 1 }
                }
            }
        }
        def outer = [throwingMap, "outer-tail"] as Object[]

        when:
        def rendered = renderAt(outer)

        then:
        noExceptionThrown()
        rendered.contains("...[SOE]")
        !rendered.contains("outer-tail")
    }

    def "collection iterator path swallows render graph abort signal across Object array boundary"() {
        given:
        def throwingList = new AbstractList<String>() {
            @Override String get(int index) { return "x" }

            @Override int size() { return 1 }

            @Override Iterator<String> iterator() {
                return new Iterator<String>() {
                    @Override boolean hasNext() { throw new StackOverflowError("deep") }

                    @Override String next() { return "x" }
                }
            }
        }
        def outer = [throwingList, "outer-tail"] as Object[]

        when:
        def rendered = renderAt(outer)

        then:
        noExceptionThrown()
        rendered.contains("...[SOE]")
        !rendered.contains("outer-tail")
    }

    def "public boundary writes abort marker in place of container closure when abort signal interrupts rendering"() {
        when:
        def rendered = renderAt(value)

        then:
        noExceptionThrown()
        rendered == expected

        where:
        value                         | expected
        [abortLoggable()]             | "[...[SOE]"
        [k: abortLoggable()]          | "{k=...[SOE]"
        [abortLoggable()] as Object[] | "[...[SOE]"
    }

    def "render preserves prior sibling and emits single abort marker when middle element of Object array triggers SOE"() {
        given:
        def value = ["before", abortLoggable(), "after"] as Object[]

        when:
        def rendered = renderAt(value)

        then:
        noExceptionThrown()
        rendered == "[before, ...[SOE]"
    }

    def "abort marker degrades under tight budget across full SOE propagation chain"() {
        when:
        def rendered = renderAt(abortLoggable(), budget)

        then:
        noExceptionThrown()
        rendered == expected

        where:
        budget | expected
        3      | "..."
        4      | "...["
        8      | "...[SOE]"
    }

    def "two sequential renders on a shared StringBuilder isolate the SOE swallow so the second render produces clean container output"() {
        given: "an Object array with a Loggable that throws SOE in the middle, plus a healthy follow-up array"
        def crashing = ["head", abortLoggable(), "tail"] as Object[]
        def healthy = ["safe-1", "safe-2"] as Object[]
        def builder = new StringBuilder()

        when: "the crashing render runs first; a separator is added; then a fresh StringBuilderWithContext renders the healthy payload onto the same builder"
        new StringBuilderWithContext(builder).tap { setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength()) }.render(crashing)
        builder.append('|')
        new StringBuilderWithContext(builder).tap { setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength()) }.render(healthy)

        then: "the crashing render emits the SOE marker before 'tail' and the subsequent render writes the full healthy container unaffected by prior truncation latch"
        noExceptionThrown()
        builder.toString().contains("...[SOE]")
        !builder.toString().contains("tail")
        builder.toString().endsWith("|[safe-1, safe-2]")
    }

    def "concurrent renders isolate per-task SOE swallow output without cross-task leakage or interference with healthy tasks"() {
        given: "24 crashing tasks rendering an Object array whose middle element throws SOE, and 24 healthy tasks rendering a plain Object array"
        def crashing = ["head", abortLoggable(), "tail"] as Object[]
        def healthy = ["safe-1", "safe-2"] as Object[]
        def executor = java.util.concurrent.Executors.newFixedThreadPool(6)
        def crashingFutures = (1..24).collect {
            executor.submit({ ->
                def b = new StringBuilder()
                new StringBuilderWithContext(b).tap { setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength()) }.render(crashing)
                b.toString()
            } as java.util.concurrent.Callable<String>)
        }
        def healthyFutures = (1..24).collect {
            executor.submit({ ->
                def b = new StringBuilder()
                new StringBuilderWithContext(b).tap { setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength()) }.render(healthy)
                b.toString()
            } as java.util.concurrent.Callable<String>)
        }

        when:
        def crashingResults = crashingFutures.collect { it.get() }
        def healthyResults = healthyFutures.collect { it.get() }

        then: "every crashing task carries its own SOE marker without any 'tail' leakage; every healthy task carries the canonical container output with no SOE marker contamination from sibling tasks"
        crashingResults.every { it.contains("...[SOE]") && !it.contains("tail") }
        healthyResults.every { it == "[safe-1, safe-2]" }

        cleanup:
        executor.shutdownNow()
    }

    def "public entry rethrows non-abort Error unchanged across all shouldRethrow renderer paths"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder).tap { setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength()) }

        when:
        context.render(valueFactory())

        then:
        def ex = thrown(InternalError)
        ex.message == expectedMessage
        builder.toString() == expectedPrefix

        where:
        [valueFactory, expectedMessage, expectedPrefix] << [
                [{
                    ->
                        new Number() {
                            @Override int intValue() { return 0 }

                            @Override long longValue() { return 0L }

                            @Override float floatValue() { return 0.0f }

                            @Override double doubleValue() { return 0.0d }

                            @Override String toString() { throw new InternalError("boom-append") }
                        }
                }, "boom-append", ""],
                [{
                    ->
                        new AbstractList<String>() {
                            @Override String get(int index) { return "x" }

                            @Override int size() { return 1 }

                            @Override Iterator<String> iterator() {
                                new Iterator<String>() {
                                    boolean hasNext() { throw new InternalError("boom-collection") }

                                    String next() { return "x" }
                                }
                            }
                        }
                }, "boom-collection", "["],
                [{
                    ->
                        new AbstractMap<String, String>() {
                            @Override
                            Set<Map.Entry<String, String>> entrySet() {
                                new AbstractSet<Map.Entry<String, String>>() {
                                    @Override
                                    Iterator<Map.Entry<String, String>> iterator() {
                                        new Iterator<Map.Entry<String, String>>() {
                                            boolean hasNext() { throw new InternalError("boom-map") }

                                            Map.Entry<String, String> next() { return null }
                                        }
                                    }

                                    @Override
                                    int size() { return 1 }
                                }
                            }
                        }
                }, "boom-map", "{"],
                [{ -> [({ StringBuilderWithContext ctx -> throw new InternalError("boom-array") } as Loggable)] as Object[] }, "boom-array", "["],
                [{ -> ({ StringBuilderWithContext ctx -> throw new InternalError("boom-loggable") } as Loggable) }, "boom-loggable", ""]
        ]
    }

    def "nested loggable chain emits a single MAX_DEPTH marker and preserves outer renderDepth balance after the chain unwinds"() {
        given: "a self-recursive Loggable chain that re-enters via ctx.render(...) until MAX_RENDER_DEPTH saturates"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength())
        int callCount = 0
        Loggable[] holder = new Loggable[1]
        Loggable recurse = { ctx ->
            callCount++
            ctx.render(holder[0])
        } as Loggable
        holder[0] = recurse

        when:
        LoggableRenderer.INSTANCE.render(context, recurse)

        then: "the depth guard fired — context is truncated"
        context.isTruncated()

        and: "the output ends with the MAX_DEPTH marker — the depth guard wrote it once during the deepest frame"
        builder.toString().endsWith(StringBuilderWithContext.MAX_DEPTH_MARKER)

        and: "the MAX_DEPTH marker appears exactly once — forceAppendAuditMarker enforced first-writer-wins"
        builder.toString().count(StringBuilderWithContext.MAX_DEPTH_MARKER) == 1

        and: "the loggable was invoked exactly MAX_RENDER_DEPTH times — once per accepted frame, and zero times for the frame that hit the limit"
        callCount == StringBuilderWithContext.MAX_RENDER_DEPTH

        and: "every accepted-frame finally executed exitRenderDepth — the renderDepth counter is back to zero (verified directly; the post-MAX_DEPTH truncated latch now short-circuits enterRenderDepth so probing it would conflate the two invariants)"
        context.@renderDepth == 0
    }

    def "outer loggable can complete its remaining writes after a nested loggable rethrows InternalError"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength())
        Loggable innerErr = { ctx -> throw new InternalError("boom") } as Loggable
        boolean tailReached = false
        Loggable outer = { ctx ->
            ctx.append("head|")
            try {
                ctx.render(innerErr)
            } catch (InternalError ignored) {
                // outer deliberately catches to verify it can continue writing
            }
            ctx.append("|tail")
            tailReached = true
        } as Loggable

        when:
        LoggableRenderer.INSTANCE.render(context, outer)

        then: "no Error propagated past the outer Loggable's catch"
        noExceptionThrown()

        and: "the outer reached its tail write — proving its finally chain was unblocked by the inner rethrow"
        tailReached

        and: "the builder contains the outer's full output without any inner write — the inner Error path neither flushed text nor wrote a fallback marker"
        builder.toString() == "head||tail"

        and: "the context is not truncated — InternalError rethrow does not flip the truncation latch"
        !context.isTruncated()

        and: "both outer and inner finally blocks ran exitRenderDepth — renderDepth is balanced for further reuse"
        context.enterRenderDepth()
        context.exitRenderDepth()
    }

    private static String renderAt(Object payload, int budget = LogRuntime.getGlobalMaxMessageLength()) {
        def builder = new StringBuilder()
        new StringBuilderWithContext(builder).tap { setMaxMessageLength(budget) }.render(payload)
        return builder.toString()
    }

    private static Loggable abortLoggable() {
        return ({ StringBuilderWithContext ctx ->
            throw new StackOverflowError("deep")
        } as Loggable)
    }

    private static CharSequence soeCharSequence() {
        return new CharSequence() {
            @Override
            int length() {
                throw new StackOverflowError("deep")
            }

            @Override
            char charAt(int index) {
                return 'x'
            }

            @Override
            CharSequence subSequence(int start, int end) {
                return ""
            }

            @Override
            String toString() {
                return "x"
            }
        }
    }
}
