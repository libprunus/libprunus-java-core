package org.libprunus.core.log.runtime

import java.util.ConcurrentModificationException
import java.util.NoSuchElementException
import java.util.RandomAccess
import org.libprunus.core.log.runtime.fixture.CollectionRendererFixtures
import spock.lang.Specification

class CollectionRendererSpec extends Specification {

    def setupSpec() {
        LogRuntimeTestSupport.resetBinding()
    }

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

    private static AbstractList<String> listWithIteratorYieldingThenThrowing(
            Throwable error, int totalSize, String element = "item") {
        CollectionRendererFixtures.listWithIteratorYieldingThenThrowing(error, totalSize, element)
    }

    /**
     * Named subclass used to lock the renderer's {@code instanceof List<?> list && list instanceof RandomAccess}
     * route — exercises the RandomAccess branch with a non-ArrayList List that explicitly implements RandomAccess.
     */
    static class TrackingRandomAccessList extends AbstractList<String> implements RandomAccess {
        List<String> elements
        int getCount = 0
        boolean iteratorUsed = false

        @Override
        String get(int i) {
            getCount++
            return elements[i]
        }

        @Override
        int size() { return elements.size() }

        @Override
        Iterator<String> iterator() {
            iteratorUsed = true
            return super.iterator()
        }
    }

    /**
     * Named subclass used to lock the renderer's two-predicate route — a RandomAccess Collection that
     * is NOT a List, so the iterator branch must still be taken because the route requires both predicates.
     */
    static class TrackingRandomAccessNonListCollection extends AbstractCollection<String> implements RandomAccess {
        List<String> elements
        boolean iteratorUsed = false

        @Override
        int size() { return elements.size() }

        @Override
        Iterator<String> iterator() {
            iteratorUsed = true
            return elements.iterator()
        }
    }

    private static AbstractCollection<String> collectionWithIteratorAlwaysThrowing(Throwable error) {
        new AbstractCollection<String>() {
            @Override
            int size() { return 1 }
            @Override
            Iterator<String> iterator() {
                new Iterator<String>() {
                    @Override
                    boolean hasNext() { return true }
                    @Override
                    String next() { throw error }
                }
            }
        }
    }

    // === Section A: Happy-Path Orchestration ===

    def "render early-returns when context is already truncated and does not invoke isEmpty or iterator on the collection"() {
        given: "an unlimited-budget context that is latched truncated by markRenderTruncation before render is invoked"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        context.markRenderTruncation()
        def snapshotAfterTruncationLatch = builder.toString()
        boolean isEmptyCalled = false
        boolean iteratorCalled = false
        def instrumented = new AbstractCollection<String>() {
            @Override
            boolean isEmpty() {
                isEmptyCalled = true
                return false
            }
            @Override
            Iterator<String> iterator() {
                iteratorCalled = true
                return Collections.emptyIterator()
            }
            @Override
            int size() { return 1 }
        }

        when:
        CollectionRenderer.INSTANCE.render(context, instrumented)

        then: "no exception propagates from the early-return path"
        noExceptionThrown()

        and: "the renderer never reached the try block — neither isEmpty() nor iterator() was probed"
        !isEmptyCalled
        !iteratorCalled

        and: "the builder content is exactly the post-truncation-latch snapshot — no '[' was committed, no element was written"
        builder.toString() == snapshotAfterTruncationLatch
        !builder.toString().endsWith("[")
    }

    def "halts rendering and emits depth marker when render depth limit is already reached after opening bracket"() {
        given: "a context already saturated at the maximum render depth"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }

        when: "render is invoked on a non-empty collection"
        CollectionRenderer.INSTANCE.render(context, [1, 2, 3])

        then: "context is truncated with the depth marker and no element content is written"
        context.isTruncated()
        builder.toString().contains("MAX_DEPTH")
        !builder.toString().contains("1")
    }

    def "render short-circuits empty collection via isEmpty without allocating an Iterator"() {
        given: "a custom empty collection that tracks whether iterator() is called"
        def builder = new StringBuilder()
        boolean iteratorCalled = false
        def emptyCol = new AbstractCollection() {
            @Override
            boolean isEmpty() { return true }
            @Override
            Iterator iterator() {
                iteratorCalled = true
                return Collections.emptyIterator()
            }
            @Override
            int size() { return 0 }
        }

        when: "the empty collection is rendered"
        CollectionRenderer.INSTANCE.render(contextOf(builder, 0, -1), emptyCol)

        then: "output is the empty bracket pair and the iterator was never allocated"
        builder.toString() == "[]"
        !iteratorCalled
    }

    def "render on empty collection still balances render depth via finally so a subsequent deeply-nested render is unaffected"() {
        given: "an unlimited-budget context pre-pushed to MAX_RENDER_DEPTH - 2 so the first empty render leaves exactly one slot for a nested element render afterwards"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        (StringBuilderWithContext.MAX_RENDER_DEPTH - 2).times { context.enterRenderDepth() }

        when: "the first render exercises the isEmpty-short-circuit path which must still hit the finally that decrements renderDepth"
        CollectionRenderer.INSTANCE.render(context, [])

        and: "after resetting the builder content (but not the depth counter), a second render of a one-level-nested collection is invoked on the same context"
        builder.setLength(0)
        CollectionRenderer.INSTANCE.render(context, [["a"]])

        then: "the second render produces the fully bracketed nested output — proving the empty render's finally decremented the depth so the inner element render still had one free slot"
        builder.toString() == "[[a]]"

        and: "no MAX_DEPTH marker appears — proving the depth counter did not silently retain the first render's increment"
        !builder.toString().contains("MAX_DEPTH")

        and: "the context is not in a truncated state — proving the empty render's clean exit left no audit-marker residue"
        !context.isTruncated()
    }

    def "render produces canonical bracketed output across RandomAccess and iterator paths over empty, multi-element, and null-containing collections"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when:
        CollectionRenderer.INSTANCE.render(context, collection)

        then:
        builder.toString() == expected

        where: "RandomAccess (ArrayList literal) and iterator (LinkedHashSet, LinkedList) paths exercise empty / non-empty / null-containing / multi-element / nested shapes"
        collection                              || expected
        ["item1", "item2"]                      || "[item1, item2]"
        [42]                                    || "[42]"
        []                                      || "[]"
        new LinkedHashSet<>(["item1", "item2"]) || "[item1, item2]"
        new LinkedList<>([10, 20, 30])          || "[10, 20, 30]"
        new LinkedList<>([])                    || "[]"
        ["A", null, "B"]                        || "[A, null, B]"
        new LinkedList<>(["A", null, "B"])      || "[A, null, B]"
        [["a", "b"], ["c"]]                     || "[[a, b], [c]]"
    }

    def "render renders the same nested collection twice when it appears at two sibling positions without cycle"() {
        given: "a single shared inner list referenced from two sibling positions of an outer list"
        def shared = [1, 2]
        def builder = new StringBuilder(256)

        when:
        CollectionRenderer.INSTANCE.render(contextOf(builder, 0, -1), [shared, shared])

        then: "both sibling references render independently in full; the renderer does not treat the second reference as a cycle"
        builder.toString() == "[[1, 2], [1, 2]]"
    }

    def "render produces hierarchical bracket output for nested collections"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)

        when:
        CollectionRenderer.INSTANCE.render(context, [["X", "Y"]])

        then:
        builder.toString() == "[[X, Y]]"
        !context.isTruncated()
    }

    def "render routes a custom RandomAccess List subclass through the get(int) path rather than iterator"() {
        given: "a custom AbstractList marked RandomAccess via a named static class that tracks get(int) count and flags iterator() if it were ever invoked"
        def tracker = new TrackingRandomAccessList(elements: ["a", "b"])
        def builder = new StringBuilder()

        when:
        CollectionRenderer.INSTANCE.render(contextOf(builder, 0, -1), tracker)

        then: "get(int) was used for every element — proving the RandomAccess branch was taken"
        tracker.getCount == 2

        and: "iterator() was never called — proving the route did not silently fall back to the iterator branch"
        !tracker.iteratorUsed

        and: "the output is the canonical bracketed form"
        builder.toString() == "[a, b]"
    }

    def "render routes a non-List RandomAccess collection through the iterator path because the route requires both predicates"() {
        given: "an AbstractCollection (not a List) that also implements RandomAccess and tracks iterator() usage"
        def tracker = new TrackingRandomAccessNonListCollection(elements: ["a"])
        def builder = new StringBuilder()

        when:
        CollectionRenderer.INSTANCE.render(contextOf(builder, 0, -1), tracker)

        then: "the iterator path produced the canonical bracketed output — proving the route's `instanceof List<?>` predicate is enforced even when RandomAccess is present"
        builder.toString() == "[a]"

        and: "iterator() was called — positive evidence that the route reached the iterator branch"
        tracker.iteratorUsed
    }

    def "render preserves the builder prefix and appends collection content under unlimited budget for single-null and prefix-bearing shapes"() {
        given:
        def builder = new StringBuilder(prefix)

        when:
        CollectionRenderer.INSTANCE.render(contextOf(builder, builder.length(), -1), collection)

        then:
        builder.toString() == expected

        where:
        prefix | collection || expected
        ""     | [null]     || "[null]"
        "pre"  | ["hi"]     || "pre[hi]"
    }

    // === Budget / truncation boundary tests ===

    def "render respects maxObjectLength budget covering openArray-blocked, element-blocked, separator-blocked, and separator-committed-but-element-rejected paths"() {
        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, budget), [1, 2, 3]) }

        then:
        result == expected

        where: "budgets exercise each truncation decision point on the RandomAccess path"
        budget || expected
        -1     || "[1, 2, 3]"
        1000   || "[1, 2, 3]"
        5      || "[1..."
        4      || "[..."
        3      || "..."
        2      || ".."
        1      || "."
        0      || ""
    }

    def "render respects maxObjectLength budget for non-RandomAccess iterator path covering same boundary paths"() {
        given:
        def linkedList = new LinkedList<>([1, 2, 3])

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, budget), linkedList) }

        then:
        result == expected

        where: "budgets exercise each truncation decision point on the iterator path"
        budget || expected
        -1     || "[1, 2, 3]"
        5      || "[1..."
        4      || "[..."
        3      || "..."
        2      || ".."
        1      || "."
        0      || ""
    }

    def "render terminates when iterator hasNext never returns false, bounded by absoluteCap to prevent infinite loop"() {
        given: "a non-RandomAccess collection whose iterator always reports hasNext()=true"
        def infiniteSet = new AbstractCollection<String>() {
            @Override
            int size() { return Integer.MAX_VALUE }

            @Override
            Iterator<String> iterator() {
                new Iterator<String>() {
                    boolean hasNext() { return true }
                    String next() { return "x" }
                }
            }
        }

        when: "the unbounded collection is rendered with a tight budget of 10"
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, 10), infiniteSet) }

        then: "absoluteCap prevents runaway iteration and emits the truncation marker"
        result == "[x, x, ..."
    }

    def "render halts iteration and leaves subsequent elements unrendered when appendObjectTo exhausts budget for a middle element"() {
        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, 9), ["first", "second", "third"]) }

        then:
        result == "[first..."
        !result.contains("third")
    }

    def "render correctly calculates absoluteCap relative to objectStartLength when embedded in a larger builder context"() {
        given:
        def builder = new StringBuilder("outer")

        when:
        CollectionRenderer.INSTANCE.render(contextOf(builder, 5, 4), ["A", "B"])

        then:
        builder.toString() == "outer[..."
        !builder.toString().contains("B")
    }

    def "render resets builder to absoluteCap boundary when openArray is blocked by exhausted budget"() {
        given:
        def builder = new StringBuilder("prefix")

        when:
        CollectionRenderer.INSTANCE.render(contextOf(builder, 0, 0), ["A", "B"])

        then:
        builder.toString() == ""
    }

    def "render rewinds emitted elements and emits truncation marker when closing bracket cannot fit within budget"() {
        when:
        def rendered = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, 5), ["A", "B"]) }

        then:
        rendered == "[A..."
    }

    def "render emits closing bracket for empty collection only when budget accommodates it and rewinds via truncation marker otherwise"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, budget)

        when:
        CollectionRenderer.INSTANCE.render(context, Collections.emptyList())

        then:
        builder.toString() == expected
        context.isTruncated() == truncated

        where: "budget straddles the boundary that fits the closing bracket of an empty collection"
        budget || expected || truncated
        2      || "[]"     || false
        1      || "."      || true
    }

    // === Exception / CME / SOE handling tests ===

    def "render treats IndexOutOfBoundsException during RandomAccess traversal as CME marker"() {
        given: "an ArrayList subclass that throws IOOBE on get() after the first call"
        def shrinkingList = new ArrayList<String>() {
            int callCount = 0

            @Override
            boolean isEmpty() { return false }

            @Override
            String get(int index) {
                if (callCount++ > 0) throw new IndexOutOfBoundsException("simulated shrink at index " + index)
                return "first"
            }

            @Override
            int size() { return 5 }
        }

        when: "the concurrently shrinking list is rendered"
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), shrinkingList) }

        then: "partial content is retained and the CME marker is appended"
        result == "[first, ...[CME]"
    }

    def "render treats IOOBE thrown on get(0) of a completely broken RandomAccess list as CME marker"() {
        given: "an ArrayList subclass that always throws IOOBE on any get() call"
        def brokenList = new ArrayList<String>() {
            @Override
            boolean isEmpty() { return false }

            @Override
            String get(int index) { throw new IndexOutOfBoundsException("always broken at index " + index) }

            @Override
            int size() { return 10 }
        }

        when: "the broken list is rendered"
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), brokenList) }

        then: "partial content is rolled back and only the CME marker remains inside the brackets"
        result == "[...[CME]"
    }

    def "render treats ConcurrentModificationException thrown directly by RandomAccess get as CME marker"() {
        given: "a RandomAccess list whose get throws CME (not IOOBE) after the first call"
        def cmeList = new java.util.AbstractList<Object>() {
            @Override
            Object get(int index) {
                if (index == 0) return 42
                throw new ConcurrentModificationException("simulated random access")
            }
            @Override
            int size() { return 2 }
        }

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), cmeList) }

        then:
        noExceptionThrown()
        result == "[42, ...[CME]"
    }

    def "render treats ConcurrentModificationException from iterator as CME marker"() {
        given:
        def cmeList = listWithIteratorYieldingThenThrowing(new ConcurrentModificationException(), 3)

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), cmeList) }

        then:
        result == "[item, ...[CME]"
    }

    def "render CME marker overwrites partial content when remaining budget is less than marker length"() {
        given:
        def cmeList = listWithIteratorYieldingThenThrowing(new ConcurrentModificationException(), 2, "abcdefghij")

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, 20), cmeList) }

        then:
        result == "[abcdefghij,...[CME]"
    }

    def "render CME marker uses marker prefix when object budget is smaller than marker length"() {
        given:
        def cmeCollection = collectionWithIteratorAlwaysThrowing(new ConcurrentModificationException())

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, budget), cmeCollection) }

        then:
        result == expected

        where: "budgets exercise each prefix length of the CME marker"
        budget || expected
        1      || "."
        2      || ".."
        3      || "..."
        4      || "...["
        5      || "...[C"
        6      || "...[CM"
    }

    def "render does not append the closing bracket after the CME marker on any CME path"() {
        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), collectionFactory()) }

        then: "the output terminates with the CME marker"
        result.endsWith("[CME]")

        and: "no trailing ']' was appended after the CME marker — the catch block returned before the post-finally append(']') would run"
        !result.endsWith("]]")

        where: "three CME trigger sources cover RandomAccess get IOOBE, RandomAccess get CME, and iterator next CME — isEmpty-CME is covered by CollectionRendererAlgorithmSpec under the unified colFactory table"
        collectionFactory << [
            {
                new ArrayList<String>() {
                    @Override boolean isEmpty() { return false }
                    @Override String get(int index) {
                        if (index == 0) return "first"
                        throw new IndexOutOfBoundsException("simulated shrink at index " + index)
                    }
                    @Override int size() { return 5 }
                }
            },
            {
                new AbstractList<Object>() {
                    @Override Object get(int index) {
                        if (index == 0) return 42
                        throw new ConcurrentModificationException("simulated random access")
                    }
                    @Override int size() { return 2 }
                }
            },
            { listWithIteratorYieldingThenThrowing(new ConcurrentModificationException(), 3) }
        ]
    }

    def "render appends the dynamically extracted exception class name for any non-CME exception thrown during traversal"() {
        given:
        def throwingList = listWithIteratorYieldingThenThrowing(exceptionToThrow, 3)

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), throwingList) }

        then:
        result == "[item, ...[$expectedMarker]"

        where: "each exception type maps to its canonical class name in the marker"
        exceptionToThrow                                      || expectedMarker
        new RuntimeException("generic error")                 || "java.lang.RuntimeException"
        new IllegalStateException("Database connection lost") || "java.lang.IllegalStateException"
        new NoSuchElementException()                          || "java.util.NoSuchElementException"
    }

    def "render does not append the closing bracket after the throwable fallback marker for any non-CME exception"() {
        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), listWithIteratorYieldingThenThrowing(throwable, 3)) }

        then: "the output terminates with the fallback marker (RuntimeException FQCN, NoSuchElementException FQCN, or [SOE])"
        result.endsWith(expectedEnding)

        and: "no trailing ']' was appended after the marker — the catch block returned before the post-finally append(']') would run"
        !result.endsWith("]]")

        where: "non-CME exception types route through the Throwable fallback marker"
        throwable                                 || expectedEnding
        new RuntimeException("rte")               || "[java.lang.RuntimeException]"
        new NoSuchElementException()              || "[java.util.NoSuchElementException]"
        new StackOverflowError("soe")             || "[SOE]"
    }

    def "render labels the full exception class name when iterator throws RuntimeException on the very first element"() {
        given:
        def throwingCollection = collectionWithIteratorAlwaysThrowing(new RuntimeException("boom"))

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), throwingCollection) }

        then:
        result == "[...[java.lang.RuntimeException]"
    }

    def "render fallback marker for an anonymous-subclass exception type still surfaces the JVM-assigned class name via getClass().getName()"() {
        given: "an iterator that throws an anonymous subclass of RuntimeException so the fallback marker must pull a class name that contains an enclosing \$N suffix"
        def anonymous = new RuntimeException("anon") {}
        def anonymousFqcn = anonymous.getClass().getName()
        def throwingList = listWithIteratorYieldingThenThrowing(anonymous, 3)

        when:
        def result = render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), throwingList) }

        then: "the fallback marker uses the FQCN of the anonymous class (appendThrowableFallback calls getClass().getName(), so the marker carries the enclosing-class\$N form)"
        result == "[item, ...[${anonymousFqcn}]"

        and: "the marker is not the simple name nor the enclosing class — guards against a refactor switching to getSimpleName() which would yield an empty string for anonymous subclasses"
        anonymousFqcn.startsWith(CollectionRendererSpec.name + '$')
        !result.endsWith("[]")
        !result.endsWith("[RuntimeException]")

        and: "no trailing closing bracket was appended after the marker"
        !result.endsWith("]]")
    }

    def "render routes ClassCastException from the value cast through handleRenderError fallback marker without escaping when value is not a Collection"() {
        given:
        def builder = new StringBuilder()

        when:
        CollectionRenderer.INSTANCE.render(contextOf(builder, 0, -1), value)

        then: "no exception escapes — the cast inside the try block is routed through the catch (Throwable) bucket"
        noExceptionThrown()

        and: "the fallback marker carries the CCE FQCN written by appendThrowableFallback, and the trailing ']' was skipped by the catch's return"
        builder.toString() == "[...[java.lang.ClassCastException]"

        where: "non-Collection inputs exercise the in-try cast failure path"
        value           || _
        "I am a String" || _
        12345           || _
        new Object()    || _
    }

    def "render propagates fatal VM Error subclasses during iteration without swallowing"() {
        given:
        def throwingList = listWithIteratorYieldingThenThrowing(fatalError, 3)

        when:
        render { b -> CollectionRenderer.INSTANCE.render(contextOf(b, 0, -1), throwingList) }

        then:
        def ex = thrown(Error)
        ex.is(fatalError)

        where: "fatal JVM Errors excluding StackOverflowError are rethrown unchanged"
        fatalError                                || _
        new OutOfMemoryError("Java heap space")   || _
        new VirtualMachineError("vm error") {}    || _
        new AssertionError("assert")              || _
    }

    def "render does not append the closing bracket and leaves the builder snapshot intact when a fatal Error is rethrown after partial element output"() {
        given: "a context with unlimited budget and a list whose iterator yields 'item' once and then throws OOM"
        def builder = new StringBuilder()
        def ctx = contextOf(builder, 0, -1)
        def fatal = new OutOfMemoryError("oom")
        def list = listWithIteratorYieldingThenThrowing(fatal, 3)

        when:
        CollectionRenderer.INSTANCE.render(ctx, list)

        then: "the exact OOM instance propagates unchanged"
        def ex = thrown(Error)
        ex.is(fatal)

        and: "the builder retains only the partial render state observed up to the rethrow point — the opening bracket, the first element, the separator written before next() threw — but no closing ']' and no fallback marker on the rethrow path"
        builder.toString() == "[item, "
    }

    def "render lets exitRenderDepth run via finally when a fatal Error is rethrown so the context can be reused for a subsequent render"() {
        given: "a context with unlimited budget that will first see a fatal Error rethrown, then be reused for a clean render"
        def builder = new StringBuilder()
        def ctx = contextOf(builder, 0, -1)
        def list = listWithIteratorYieldingThenThrowing(new OutOfMemoryError("oom"), 3)

        when: "the first render throws the fatal Error which we swallow at the test boundary"
        try {
            CollectionRenderer.INSTANCE.render(ctx, list)
        } catch (Error ignored) {
        }

        and: "after resetting the builder content, a second clean render is invoked on the same context"
        builder.setLength(0)
        CollectionRenderer.INSTANCE.render(ctx, ["recovered"])

        then: "the second render produces a fully bracketed output — proving exitRenderDepth ran via finally on the rethrow path and left the depth counter balanced"
        builder.toString() == "[recovered]"
    }

    def "render leaves the context reusable after an absorbed CME once the context is explicitly reset so that a subsequent render of an unrelated collection is unaffected"() {
        given: "a context with unlimited budget that will first see a CME absorbed, then be explicitly reset and reused for a clean render"
        def builder = new StringBuilder()
        def ctx = contextOf(builder, 0, -1)
        def cmeList = listWithIteratorYieldingThenThrowing(new ConcurrentModificationException(), 3)

        when: "the first render absorbs the CME and returns"
        CollectionRenderer.INSTANCE.render(ctx, cmeList)

        then: "the CME absorption did append the audit marker and latched the truncated state — that is the documented effect of forceAppendAuditMarker"
        ctx.isTruncated()

        when: "the context is explicitly reset (the only documented way to clear truncated/auditMarkerAppended) and reused for a clean render"
        ctx.reset(Integer.MAX_VALUE)
        CollectionRenderer.INSTANCE.render(ctx, ["ok"])

        then: "the second render produces a fully bracketed output — proving the CME absorption did not leave residual depth state that survives reset"
        builder.toString() == "[ok]"

        and: "the context is no longer in a truncated state after reset and a successful render"
        !ctx.isTruncated()
    }

    def "render terminates with MAX_DEPTH marker when a self-referential list nests beyond MAX_RENDER_DEPTH instead of stack overflow"() {
        given: "a self-referential list whose only element is itself, plus an unlimited-budget context"
        def self = []
        self.add(self)
        def builder = new StringBuilder()
        def ctx = contextOf(builder, 0, -1)

        when:
        CollectionRenderer.INSTANCE.render(ctx, self)

        then: "no StackOverflowError leaked out — the MAX_RENDER_DEPTH guard cut the recursion"
        noExceptionThrown()

        and: "the output contains the MAX_DEPTH marker — proving the depth guard fired"
        builder.toString().contains("MAX_DEPTH")

        and: "the context flagged itself truncated — proving the depth marker came from forceAppendAuditMarker, not from a successful render"
        ctx.isTruncated()
    }

    def "render emits [...[SOE] without rethrow regardless of whether StackOverflowError surfaces from iterator.next() or iterator.hasNext()"() {
        given:
        def builder = new StringBuilder()
        def context = contextOf(builder, 0, -1)
        def throwingCollection = collectionFactory()

        when:
        CollectionRenderer.INSTANCE.render(context, throwingCollection)

        then:
        noExceptionThrown()
        builder.toString() == "[...[SOE]"

        where: "either entry point of the iterator contract may raise SOE — both must route through handleRenderError to the canonical marker"
        throwingPoint || collectionFactory
        "next"        || { collectionWithIteratorAlwaysThrowing(new StackOverflowError("deep recursion")) }
        "hasNext"     || {
            new AbstractCollection<String>() {
                @Override
                int size() { return 1 }
                @Override
                Iterator<String> iterator() {
                    new Iterator<String>() {
                        @Override
                        boolean hasNext() { throw new StackOverflowError("abort") }
                        @Override
                        String next() { return "x" }
                    }
                }
            }
        }
    }

    def "render with non-Collection value lets the in-try ClassCastException flow through handleRenderError while finally restores renderDepth so the context is reusable"() {
        given: "a context whose depth counter we snapshot before invoking the renderer with a non-Collection value"
        def builder = new StringBuilder()
        def ctx = contextOf(builder, 0, -1)
        def initialDepth = ctx.@renderDepth

        when: "the renderer is invoked with a value that fails the (Collection<?>) cast"
        CollectionRenderer.INSTANCE.render(ctx, "I am not a Collection")

        then: "no exception escapes — the catch (Throwable) bucket absorbed the CCE via handleRenderError"
        noExceptionThrown()

        and: "renderDepth has been restored to its pre-call value — the finally block ran and exitRenderDepth balanced the prior enterRenderDepth"
        ctx.@renderDepth == initialDepth

        and: "the context is reusable for a subsequent clean render — proving no residual depth leak (the production bug this case guards)"
        ctx.reset(Integer.MAX_VALUE)
        builder.setLength(0)
        CollectionRenderer.INSTANCE.render(ctx, ["recovered"])
        builder.toString() == "[recovered]"
        ctx.@renderDepth == initialDepth
    }

}
