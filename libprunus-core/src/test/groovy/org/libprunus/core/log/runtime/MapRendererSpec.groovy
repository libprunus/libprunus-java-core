package org.libprunus.core.log.runtime

import java.util.ConcurrentModificationException
import java.util.NoSuchElementException
import org.libprunus.core.log.runtime.fixture.ExplodingEntryMap
import spock.lang.Specification

class MapRendererSpec extends Specification {

    private static String render(Closure<?> call) {
        def builder = new StringBuilder()
        call(builder)
        builder.toString()
    }

    private static StringBuilderWithContext contextOf(
            StringBuilder builder,
            int objectStartLength,
            int maxObjectLength) {
        def context = new StringBuilderWithContext(builder)
        int absLimit = maxObjectLength < 0 ? Integer.MAX_VALUE : objectStartLength + maxObjectLength
        context.setMaxMessageLength(absLimit)
        context
    }

    private static Map<?, ?> mapEmittingThenThrowing(Object firstKey, Object firstValue, Throwable throwOnSecondNext) {
        return new AbstractMap() {
            @Override
            Set entrySet() {
                return new AbstractSet() {
                    @Override
                    Iterator iterator() {
                        return new Iterator() {
                            int count = 0
                            @Override
                            boolean hasNext() { return count < 2 }
                            @Override
                            Object next() {
                                if (count++ == 0) return new AbstractMap.SimpleEntry(firstKey, firstValue)
                                throw throwOnSecondNext
                            }
                        }
                    }
                    @Override
                    int size() { return 1 }
                }
            }
        }
    }

    private static Map<?, ?> mapThrowingOnFirstNext(Throwable throwOnFirstNext) {
        return new AbstractMap() {
            @Override
            Set entrySet() {
                return new AbstractSet() {
                    @Override
                    Iterator iterator() {
                        return new Iterator() {
                            @Override
                            boolean hasNext() { return true }
                            @Override
                            Object next() { throw throwOnFirstNext }
                        }
                    }
                    @Override
                    int size() { return 1 }
                }
            }
        }
    }

    private static Map<?, ?> mapThrowingOnHasNext(Throwable throwOnHasNext) {
        return new AbstractMap() {
            @Override
            Set entrySet() {
                return new AbstractSet() {
                    @Override
                    Iterator iterator() {
                        return new Iterator() {
                            @Override
                            boolean hasNext() { throw throwOnHasNext }
                            @Override
                            Object next() { return null }
                        }
                    }
                    @Override
                    int size() { return 1 }
                }
            }
        }
    }

    def "render produces correct output for various map sizes with unlimited budget"() {
        expect:
        render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), map) } == expected

        where:
        map                                                            || expected
        [a: 1, b: 2]                                                   || "{a=1, b=2}"
        [x: 42]                                                        || "{x=42}"
        [:]                                                            || "{}"
        (1..10).collectEntries { ["k$it", it] } as LinkedHashMap       || "{k1=1, k2=2, k3=3, k4=4, k5=5, k6=6, k7=7, k8=8, k9=9, k10=10}"
    }

    def "render stops before separator when absoluteCap - builder.length() < 2"() {
        given: "budget=6: contentBudget=5, absoluteCap=5; after '{a=1' (length=4), 5-4=1 < 2 → separator blocked; forceAppendAuditMarker rewinds to cutPoint=2, replacing '=1' with '...'"
        def map = new LinkedHashMap<String, String>()
        map.put("a", "1")
        map.put("b", "2")

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, 6), map) }

        then:
        result == "{a=..."
    }

    def "render respects maxObjectLength on constrained budgets covering all boundary paths for a single-entry map"() {
        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, budget), ["a": "1"]) }

        then:
        result == expected

        where:
        budget || expected
        0      || ""
        1      || "."
        2      || ".."
        3      || "..."
        4      || "{..."
        5      || "{a=1}"
    }

    def "render returns early when the equals sign cannot fit after the first key"() {
        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, budget), ["abc": "1"]) }

        then: "budget=4 fills '{abc' exactly, then append('=') fails, triggerTruncationBase rewinds to '{' and writes '...'"
        result == expected

        where:
        budget || expected
        4      || "{..."
        5      || "{a..."
        6      || "{ab..."
        7      || "{abc=1}"
    }

    def "render respects maxObjectLength on constrained budgets covering separator-committed and second-entry boundary paths for a two-entry map"() {
        when:
        def result = render {
            b -> MapRenderer.INSTANCE.render(contextOf(b, 0, budget), ["a": "1", "b": "2"])
        }

        then:
        result == expected

        and: "negative: only the full-budget=10 case produces a closing brace — truncated outputs never fabricate '}'"
        budget == 10 || !result.endsWith("}")

        and: "negative: the separator ', ' is never fully committed below budget=9 — the comma+space pair only fits once the prefix is at least 6 chars"
        budget >= 8  || !result.contains(", ")

        where:
        budget || expected
        5      || "{a..."
        6      || "{a=..."
        7      || "{a=1..."
        8      || "{a=1,..."
        9      || "{a=1, ..."
        10     || "{a=1, b=2}"
    }

    def "render returns after committing two entries when the third entry's prependSeparator exhausts the remaining budget"() {
        given: "a 3-entry LinkedHashMap whose first two entries fit exactly within budget=10, so the third entry's prependSeparator triggers triggerTruncationBase"
        def map = new LinkedHashMap<String, String>()
        map.put("a", "1")
        map.put("b", "2")
        map.put("c", "3")

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, 10), map) }

        then: "after '{a=1, b=2' (length 9), separator needs maxMessageLength - 9 >= 2 which fails at budget=10; triggerTruncationBase rewinds to length 7 and appends '...'"
        result == "{a=1, b..."

        and: "negative: the third entry's key/value never reach the buffer"
        !result.contains("c")
        !result.contains("3")

        and: "negative: the closing brace is not fabricated after truncation"
        !result.endsWith("}")
    }

    def "render breaks out of entry loop when the second entry key write hits truncation after separator commits"() {
        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, budget), ["a": "1", "longKey": "v"] as LinkedHashMap) }

        then: "budget allows '{a=1, ' to commit, but appending the 7-char key 'longKey' overflows and triggerTruncation lands the '...' suffix; the break exits the while loop and the closing brace is suppressed by the latched truncated flag"
        result == expected
        !result.endsWith("}")
        !result.contains("v")

        where:
        budget || expected
        9      || "{a=1, ..."
        10     || "{a=1, l..."
        11     || "{a=1, lo..."
        12     || "{a=1, lon..."
    }

    def "render with objectStartLength set to prefix length correctly shares budget from the prefix start position"() {
        given:
        def builder = new StringBuilder("prefix")

        when: "objectStartLength=6 means budget counts from position 6; 5 chars fit exactly: {k=v}"
        MapRenderer.INSTANCE.render(contextOf(builder, 6, 5), ["k": "v"])

        then:
        builder.toString() == "prefix{k=v}"
    }

    def "render preserves builder prefix and renders null map values as the literal 'null' under unlimited budget"() {
        given:
        def builder = new StringBuilder(prefix)

        when:
        MapRenderer.INSTANCE.render(contextOf(builder, builder.length(), -1), map)

        then:
        builder.toString() == expected

        where:
        prefix | map                                                        || expected
        ""     | [k: null]                                                  || "{k=null}"
        "pre"  | [x: "y"]                                                   || "pre{x=y}"
        "ab"   | [k: "v"]                                                   || "ab{k=v}"
        ""     | (["a": null, "b": "x"] as LinkedHashMap)                   || "{a=null, b=x}"
    }

    def "render renders nested map fully without depth restriction"() {
        given:
        def nested = ["outerKey": ["innerKey": 1]]

        expect:
        render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), nested) } ==
                "{outerKey={innerKey=1}}"
    }

    def "render dispatches a Map key through appendObjectTo so a container-typed key renders via its canonical renderer"() {
        given:
        def nestedKeyMap = new LinkedHashMap<Object, Object>()
        nestedKeyMap.put(["x", "y"], 1)

        expect:
        render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), nestedKeyMap) } == "{[x, y]=1}"
    }

    def "render emits NPE fallback marker when the map argument is null"() {
        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), null) }

        then:
        noExceptionThrown()
        result == "{...[java.lang.NullPointerException]"
    }

    def "render absorbs ClassCastException as fallback marker after committing the opening brace when value is not a Map"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, 100)
        int depthBefore = context.@renderDepth

        when:
        MapRenderer.INSTANCE.render(context, new Object())

        then: "the cast moved inside try; CCE is bucketed by catch(Throwable) and recorded as fallback marker"
        noExceptionThrown()
        builder.toString().startsWith("{")
        builder.toString().contains("[java.lang.ClassCastException]")

        and: "the finally branch ran exitRenderDepth so renderDepth is back to baseline (no leak)"
        context.@renderDepth == depthBefore
    }

    def "render degrades to CME marker when map entry iteration throws ConcurrentModificationException"() {
        given:
        def cmeMap = mapEmittingThenThrowing("first", 1, new ConcurrentModificationException())

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), cmeMap) }

        then:
        noExceptionThrown()
        result == "{first=1, ...[CME]"
    }

    def "render CME marker overwrites partial content when remaining budget is less than marker length"() {
        given: "a map: first entry key=abcde value=fgh fills budget so remaining is 5 < 8 when CME fires on second next()"
        def cmeMap = mapEmittingThenThrowing("abcde", "fgh", new ConcurrentModificationException())

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, 18), cmeMap) }

        then: "partial entry content is rolled back to make room; the full CME marker is forced into the output"
        result == "{abcde=fgh...[CME]"
    }

    def "render CME marker uses marker prefix when object budget is smaller than marker length and lands the full marker once budget reaches its length"() {
        given: "a map whose very first iterator next() call throws CME, with a budget too small for the full marker"
        def cmeMap = mapThrowingOnFirstNext(new ConcurrentModificationException())
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, budget)

        when:
        MapRenderer.INSTANCE.render(context, cmeMap)

        then: "the leftmost characters of the marker that fit within the budget are written; once budget == marker length the full marker is emitted with no trailing '}'"
        builder.toString() == expected
        context.isTruncated()

        where:
        budget || expected
        0      || ""
        1      || "."
        2      || ".."
        3      || "..."
        4      || "...["
        5      || "...[C"
        6      || "...[CM"
        7      || "...[CME"
        8      || "...[CME]"
        9      || "{...[CME]"
    }

    def "render degrades to CME marker when map entry hasNext throws ConcurrentModificationException before any entry is committed"() {
        given:
        def cmeMap = mapThrowingOnHasNext(new ConcurrentModificationException("simulated"))

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), cmeMap) }

        then:
        noExceptionThrown()
        result == "{...[CME]"
    }

    def "render labels non-CME throwables with their FQCN regardless of throw site or whether an entry was already committed"() {
        given:
        def throwingMap = mapFactory(throwable)

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), throwingMap) }

        then:
        noExceptionThrown()
        result == expected
        !result.contains("...[CME]")

        where:
        scenario                                   | mapFactory                                                                       | throwable                              || expected
        "hasNext throws before any entry"          | { Throwable t -> mapThrowingOnHasNext(t) }                                       | new IllegalStateException("simulated") || "{...[java.lang.IllegalStateException]"
        "first next throws before any entry"       | { Throwable t -> mapThrowingOnFirstNext(t) }                                     | new RuntimeException("boom")           || "{...[java.lang.RuntimeException]"
        "second next throws after one entry"       | { Throwable t -> mapEmittingThenThrowing("first", 1, t) }                        | new IllegalStateException("simulated") || "{first=1, ...[java.lang.IllegalStateException]"
        "second next throws NSEE after one entry"  | { Throwable t -> mapEmittingThenThrowing("k", "v", t) }                          | new NoSuchElementException()           || "{k=v, ...[java.util.NoSuchElementException]"
    }

    def "render labels exception thrown by Map Entry getKey or getValue according to bucketing rules"() {
        given: "a single-entry map whose entry getKey or getValue throws, exercised through the renderer's catch chain"
        def explodingMap = new ExplodingEntryMap(failOnKey, error)

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), explodingMap) }

        then: "CME is bucketed to ...[CME]; non-CME RuntimeExceptions are labelled with their FQCN"
        noExceptionThrown()
        result == expected

        where:
        failOnKey | error                                  || expected
        true      | new ConcurrentModificationException()  || "{...[CME]"
        false     | new ConcurrentModificationException()  || "{k=...[CME]"
        true      | new IllegalStateException("x")         || "{...[java.lang.IllegalStateException]"
        false     | new IllegalStateException("x")         || "{k=...[java.lang.IllegalStateException]"
    }

    def "render short-circuits empty map via isEmpty without allocating Iterator"() {
        given:
        boolean iteratorCalled = false
        def emptyMap = new AbstractMap() {
            @Override
            boolean isEmpty() { return true }
            @Override
            Set entrySet() {
                return new AbstractSet() {
                    @Override
                    Iterator iterator() {
                        iteratorCalled = true
                        return Collections.emptyIterator()
                    }
                    @Override
                    int size() { return 0 }
                }
            }
        }

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), emptyMap) }

        then:
        result == "{}"
        !iteratorCalled
    }

    def "render emits empty braces when map reports non-empty but entrySet iterator yields no elements"() {
        given: "an inconsistent map: isEmpty() returns false so renderMapEntries is entered, but the iterator is immediately exhausted"
        boolean iteratorAllocated = false
        def inconsistentMap = new AbstractMap() {
            @Override
            boolean isEmpty() { return false }
            @Override
            Set entrySet() {
                return new AbstractSet() {
                    @Override
                    Iterator iterator() {
                        iteratorAllocated = true
                        return Collections.emptyIterator()
                    }
                    @Override
                    int size() { return 1 }
                }
            }
        }

        when:
        def result = render { b -> MapRenderer.INSTANCE.render(contextOf(b, 0, -1), inconsistentMap) }

        then: "the defensive '!it.hasNext()' early-return fires; iterator was allocated (distinguishing this path from the isEmpty short-circuit), and the closing brace is still appended"
        result == "{}"
        iteratorAllocated
    }

    def "render does not rethrow StackOverflowError and appends SOE fallback marker when reached through appendObjectTo dispatch"() {
        given:
        def throwingMap = mapThrowingOnHasNext(new StackOverflowError("deep recursion"))
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)

        when:
        context.appendObjectTo(throwingMap)

        then:
        noExceptionThrown()
        builder.toString() == "{...[SOE]"
    }

    def "render emits depth exceeded marker and stops when max render depth is already reached after opening brace"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }

        when:
        MapRenderer.INSTANCE.render(context, ["k": "v"])

        then:
        context.isTruncated()
        builder.toString() == "{...[MAX_DEPTH]"
    }

    def "render does not touch map iterator when render depth is already saturated after opening brace"() {
        given: "a context with depth pre-filled to MAX_RENDER_DEPTH, plus a tracking AbstractMap that records isEmpty/entrySet probes"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }

        boolean isEmptyCalled = false
        boolean entrySetCalled = false
        def probeMap = new AbstractMap() {
            @Override
            boolean isEmpty() {
                isEmptyCalled = true
                return false
            }

            @Override
            Set entrySet() {
                entrySetCalled = true
                return Collections.emptySet()
            }
        }

        when:
        MapRenderer.INSTANCE.render(context, probeMap)

        then: "the opening brace plus the MAX_DEPTH marker is the full output"
        builder.toString() == "{...[MAX_DEPTH]"

        and: "the map's isEmpty() was never invoked — depth-gate fired before any map access"
        !isEmptyCalled

        and: "the map's entrySet() was never invoked either"
        !entrySetCalled

        and: "the context was flagged truncated by forceAppendAuditMarker"
        context.isTruncated()
    }

    def "render is a no-op and does not touch map when context is already truncated on entry"() {
        given: "a context that has already latched truncated via a marker"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        context.forceAppendAuditMarker("...[TRUNCATED])")
        int before = builder.length()
        boolean touched = false
        def probeMap = new AbstractMap() {
            @Override
            boolean isEmpty() {
                touched = true
                return true
            }

            @Override
            Set entrySet() {
                touched = true
                return Collections.emptySet()
            }
        }

        when:
        MapRenderer.INSTANCE.render(context, probeMap)

        then: "the builder length did not change — append('{') returned false on the truncated short-circuit"
        builder.length() == before

        and: "the probe map was never invoked — the renderer returned before any map access"
        !touched

        and: "the context remains truncated"
        context.isTruncated()
    }

    def "render rethrows the exact same Error instance for every non-SOE Error subclass"() {
        given:
        def throwingMap = mapThrowingOnHasNext(fatalError)
        def builder = new StringBuilder()

        when:
        MapRenderer.INSTANCE.render(contextOf(builder, 0, -1), throwingMap)

        then: "the exact Error instance propagates unchanged"
        def ex = thrown(Error)
        ex.is(fatalError)

        and: "only the pre-throw opening brace was written — no fallback marker, no closing brace, no entry content"
        builder.toString() == "{"

        where: "the Error pool covers OOM, custom VirtualMachineError, and AssertionError — symmetric to CollectionRenderer's coverage"
        fatalError << [
            new OutOfMemoryError("Java heap space"),
            new VirtualMachineError("vm error") {},
            new AssertionError("assert")
        ]
    }

    def "render keeps the first CME marker latched even when subsequent forceAppendAuditMarker is invoked on the same context"() {
        given: "a CME-throwing map and a context"
        def cmeMap = mapThrowingOnHasNext(new ConcurrentModificationException())
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when: "render absorbs the CME and writes the CME marker"
        MapRenderer.INSTANCE.render(context, cmeMap)

        and: "the caller then attempts to append another audit marker"
        context.forceAppendAuditMarker(StringBuilderWithContext.RENDER_TRUNCATION_MARKER)

        then: "the second marker is suppressed by the first-wins latch — the output is still the CME marker"
        builder.toString() == "{...[CME]"

        and: "the truncated state remains set"
        context.isTruncated()
    }

    def "render on a fresh context after a previous render preserves correctness without depth or truncated-state leakage"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when: "first render produces a clean happy-path output"
        MapRenderer.INSTANCE.render(context, [a: 1])

        and: "a separator is appended and the second render runs on the same context"
        builder.append("|")
        MapRenderer.INSTANCE.render(context, [b: 2])

        then: "both maps render correctly back-to-back — proving the finally chain reset renderDepth and isTruncated remained false"
        builder.toString() == "{a=1}|{b=2}"

        and: "the context is not flagged truncated — neither render tripped the truncation latch"
        !context.isTruncated()
    }

    def "render leaves renderDepth unchanged across happy path, CME, throwable fallback, ClassCastException, and OOM rethrow exits"() {
        given: "a fresh context whose private renderDepth counter starts at 0; per Ch1 §3 Groovy direct field access is allowed in lieu of reflection"
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)
        int before = context.@renderDepth

        when: "the scenario is dispatched against the same context — swallowing the rethrown OOM so the test method itself does not propagate it"
        scenario.call(context)

        then: "renderDepth is exactly back to its pre-call value — proving the finally block called exitRenderDepth exactly once for each successful enterRenderDepth on every exit path covered here"
        context.@renderDepth == before

        where: "all five MapRenderer exit paths reachable from a happy enterRenderDepth balance enter/exit identically — the ClassCastException path is now bucketed inside the try block and joins the others"
        scenario << [
                { StringBuilderWithContext c -> MapRenderer.INSTANCE.render(c, [a: 1, b: 2]) },
                { StringBuilderWithContext c -> MapRenderer.INSTANCE.render(c, mapEmittingThenThrowing("k", 1, new ConcurrentModificationException())) },
                { StringBuilderWithContext c -> MapRenderer.INSTANCE.render(c, mapThrowingOnHasNext(new IllegalStateException("x"))) },
                { StringBuilderWithContext c -> MapRenderer.INSTANCE.render(c, new Object()) },
                { StringBuilderWithContext c -> try { MapRenderer.INSTANCE.render(c, mapThrowingOnHasNext(new OutOfMemoryError("x"))) } catch (OutOfMemoryError ignored) { } },
        ]
    }

}
