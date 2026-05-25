package org.libprunus.core.log.runtime

import spock.lang.Specification

class CollectionRendererAlgorithmSpec extends Specification {

    def setupSpec() {
        LogRuntimeTestSupport.resetBinding()
    }

    private static StringBuilderWithContext freshContext(int maxLen) {
        def ctx = new StringBuilderWithContext(new StringBuilder())
        ctx.setMaxMessageLength(maxLen)
        ctx
    }

    /**
     * Shared budget → expected-element-consumption-count grid for the renderRandomAccessList and
     * renderIterator loop-rejection feature methods. Both private paths consume their backing
     * collection through the same three rejection points (zero-budget reject on first append, gap<2
     * separator reject after element 0 fits, and mid-loop second-element appendObjectTo triggering
     * truncation), so the loop-rejection invariant is captured exactly once here.
     */
    private static final List<List<Integer>> LOOP_REJECTION_GRID = [
            [0, 1],
            [1, 1],
            [3, 2]
    ]

    def "render terminates without side effects for all reject-context and collection-type combinations"() {
        given: "a fresh context configured by the reject-type factory"
        def builder = new StringBuilder()
        def ctx = new StringBuilderWithContext(builder)
        ctxSetup.call(ctx)

        when: "render is called with a collection from the value pool"
        CollectionRenderer.INSTANCE.render(ctx, collection)

        then: "no exception propagates and no element content is written"
        noExceptionThrown()
        !builder.toString().contains("item")

        where: "Cartesian product of reject-context types × collection type variants is exhausted"
        [ctxSetup, collection] << (
            [
                [
                    { c -> c.setMaxMessageLength(0) },
                    { c ->
                        c.setMaxMessageLength(Integer.MAX_VALUE)
                        StringBuilderWithContext.MAX_RENDER_DEPTH.times { c.enterRenderDepth() }
                    }
                ],
                [
                    ["item"],
                    ["item", "item2"],
                    new LinkedHashSet<>(["item"]),
                    new LinkedList<>(["item"]),
                    Collections.emptyList()
                ]
            ].combinations()
        )
    }

    def "render rolls back the opening bracket and emits a single-char truncation marker when budget=1 cannot accommodate the closing bracket on an empty collection"() {
        given:
        def ctx = freshContext(1)

        when:
        CollectionRenderer.INSTANCE.render(ctx, [])

        then:
        ctx.isTruncated()
        ctx.builder.toString() == "."
    }

    def "render silently drops the closing bracket when mid-loop truncation latches the truncated state before the trailing append"() {
        given:
        def ctx = freshContext(4)

        when:
        CollectionRenderer.INSTANCE.render(ctx, ["a", "bbbb"])

        then:
        ctx.isTruncated()
        ctx.builder.toString() == "[..."
    }

    def "render with normal context appends CME audit marker for collections that throw ConcurrentModificationException or IndexOutOfBoundsException"() {
        given: "an unlimited budget context and an exception-throwing collection"
        def ctx = freshContext(Integer.MAX_VALUE)
        def col = colFactory()

        when: "render is called with the exception-inducing collection"
        CollectionRenderer.INSTANCE.render(ctx, col)

        then: "no exception propagates and the CME audit marker is present in the output"
        noExceptionThrown()
        ctx.builder.toString().contains("...[CME]")

        and: "the truncated latch is set by forceAppendAuditMarker on the absorbed CME path"
        ctx.isTruncated()

        where: "ConcurrentModificationException and IndexOutOfBoundsException both route to the CME marker"
        colFactory << [
            {
                new AbstractCollection<String>() {
                    @Override boolean isEmpty() { throw new ConcurrentModificationException() }
                    @Override Iterator<String> iterator() { Collections.emptyIterator() }
                    @Override int size() { return 1 }
                }
            },
            {
                new AbstractCollection<String>() {
                    @Override boolean isEmpty() { throw new IndexOutOfBoundsException("test IOOBE") }
                    @Override Iterator<String> iterator() { Collections.emptyIterator() }
                    @Override int size() { return 1 }
                }
            }
        ]
    }

    private static final class AioobeRandomAccessList extends AbstractList<String> implements RandomAccess {
        @Override
        String get(int index) {
            if (index >= 5) {
                throw new ArrayIndexOutOfBoundsException("simulated concurrent shrink at " + index)
            }
            return "e" + index
        }
        @Override
        int size() { return 10 }
    }

    def "render absorbs ArrayIndexOutOfBoundsException as CME marker on RandomAccess list"() {
        given: "a RandomAccess list whose get(i) throws ArrayIndexOutOfBoundsException for i>=5, mimicking concurrent ArrayList shrink"
        def ctx = freshContext(Integer.MAX_VALUE)
        def list = new AioobeRandomAccessList()

        when: "render dispatches the RandomAccess fast path and the AIOOBE subtype surfaces mid-loop"
        CollectionRenderer.INSTANCE.render(ctx, list)

        then: "the AIOOBE subtype is absorbed by the catch(IndexOutOfBoundsException) clause and emits the CME audit marker"
        noExceptionThrown()
        ctx.builder.toString().endsWith("...[CME]")

        and: "elements up to the throwing index were rendered before the marker latched"
        ctx.builder.toString().startsWith("[e0, e1, e2, e3, e4, ")
    }

    def "render with normal context routes SOE and RuntimeException thrown during collection processing to the Throwable fallback without rethrowing"() {
        given: "an unlimited budget context and an exception-throwing collection"
        def ctx = freshContext(Integer.MAX_VALUE)
        def col = colFactory()

        when: "render is called with the exception-inducing collection"
        CollectionRenderer.INSTANCE.render(ctx, col)

        then: "no exception propagates and the expected fallback marker substring appears in the output"
        noExceptionThrown()
        ctx.builder.toString().contains(expectedMarker)

        where: "SOE and RuntimeException each map to their respective fallback marker substrings"
        colFactory << [
            {
                new AbstractCollection<String>() {
                    @Override boolean isEmpty() { throw new StackOverflowError() }
                    @Override Iterator<String> iterator() { Collections.emptyIterator() }
                    @Override int size() { return 1 }
                }
            },
            {
                new AbstractCollection<String>() {
                    @Override boolean isEmpty() { throw new RuntimeException("rte") }
                    @Override Iterator<String> iterator() { Collections.emptyIterator() }
                    @Override int size() { return 1 }
                }
            }
        ]
        expectedMarker << ["...[SOE]", "RuntimeException"]
    }

    def "render with normal context propagates OutOfMemoryError thrown during collection processing without swallowing"() {
        given: "an unlimited budget context and a collection whose isEmpty throws OutOfMemoryError"
        def ctx = freshContext(Integer.MAX_VALUE)
        def oomError = new OutOfMemoryError("OOM in collection processing")
        def col = new AbstractCollection<String>() {
            @Override boolean isEmpty() { throw oomError }
            @Override Iterator<String> iterator() { Collections.emptyIterator() }
            @Override int size() { return 1 }
        }

        when: "render is called with the OOM-throwing collection"
        CollectionRenderer.INSTANCE.render(ctx, col)

        then: "OutOfMemoryError propagates unchanged — same instance, not wrapped — and only the pre-throw open bracket is written"
        def ex = thrown(OutOfMemoryError)
        ex.is(oomError)
        ctx.builder.toString() == "["
    }

    def "renderRandomAccessList is a no-op when the list is empty because the size==0 short-circuit precedes any budget probe"() {
        given:
        def ctx = freshContext(Integer.MAX_VALUE)

        when:
        CollectionRenderer.renderRandomAccessList(ctx, [])

        then:
        ctx.builder.toString() == ""
    }

    def "renderRandomAccessList emits comma-separated elements for the single-element and the multi-element entry points to the private path"() {
        given:
        def ctx = freshContext(Integer.MAX_VALUE)

        when:
        CollectionRenderer.renderRandomAccessList(ctx, list)

        then:
        ctx.builder.toString() == expected

        where: "size=1 locks the get(0) + return short-circuit; size=2 locks the prependSeparator + get(1) loop entry"
        list       || expected
        ["a"]      || "a"
        ["a", "b"] || "a, b"
    }

    def "renderRandomAccessList stops accessing list elements at each distinct rejection branch"() {
        given:
        def ctx = freshContext(budget)
        int getCallCount = 0
        def list = new AbstractList<String>() {
            @Override
            String get(int index) {
                getCallCount++
                return "a"
            }
            @Override
            int size() { return 3 }
        }

        when:
        CollectionRenderer.renderRandomAccessList(ctx, list)

        then:
        getCallCount == expectedCalls

        and: "every rejection branch latches truncated state via triggerTruncationBase"
        ctx.isTruncated()

        and: "no closing bracket is silently committed under any rejection branch — renderRandomAccessList never appends ']' itself"
        !ctx.builder.toString().endsWith("]")

        where: "shared LOOP_REJECTION_GRID pins each rejection point: budget=0 → first appendObjectTo rejected; budget=1 → prependSeparator rejected when gap<2; budget=3 → second appendObjectTo triggers truncation"
        [budget, expectedCalls] << LOOP_REJECTION_GRID
    }

    def "renderRandomAccessList does not access list.get(index>=1) once appendObjectTo of element 0 is rejected by a budget that cannot fit even the first element"() {
        given: "a context whose absolute cap rejects the very first appendObjectTo of a long element"
        def ctx = freshContext(1)
        int getCallCount = 0
        def list = new AbstractList<String>() {
            @Override
            String get(int index) {
                getCallCount++
                return "long-element"
            }
            @Override
            int size() { return 5 }
        }

        when:
        CollectionRenderer.renderRandomAccessList(ctx, list)

        then: "get was called exactly once — only for index 0; the for-loop body was never entered after the short-circuit"
        getCallCount == 1

        and: "the context latched truncated — proving the rejection signal that fired the short-circuit was the appendObjectTo-side cap, not a silent return"
        ctx.isTruncated()
    }

    def "renderRandomAccessList performs exactly one get and no separator call when list size is 1 under unlimited budget"() {
        given: "an unlimited budget context plus a one-element AbstractList tracking get(int) call count"
        def ctx = freshContext(Integer.MAX_VALUE)
        int getCalls = 0
        def list = new AbstractList<String>() {
            @Override
            String get(int i) {
                getCalls++
                return "only"
            }
            @Override
            int size() { return 1 }
        }

        when:
        CollectionRenderer.renderRandomAccessList(ctx, list)

        then: "exactly one get(int) call was issued — the single element was read once and the for-loop body was never entered"
        getCalls == 1

        and: "the rendered output is the single element verbatim — proving the single-element happy path emits exactly that element"
        ctx.builder.toString() == "only"
    }

    def "renderIterator is a no-op when the iterator is empty because the hasNext()==false short-circuit precedes any budget probe"() {
        given:
        def ctx = freshContext(Integer.MAX_VALUE)

        when:
        CollectionRenderer.renderIterator(ctx, Collections.emptyIterator())

        then:
        ctx.builder.toString() == ""
    }

    def "renderIterator emits comma-separated elements for the single-element and the multi-element entry points to the private path"() {
        given:
        def ctx = freshContext(Integer.MAX_VALUE)

        when:
        CollectionRenderer.renderIterator(ctx, elements.iterator())

        then:
        ctx.builder.toString() == expected

        where: "size=1 locks the first-element hasNext+next + return short-circuit; size=2 locks the while-loop hasNext+prependSeparator+next entry"
        elements   || expected
        ["a"]      || "a"
        ["a", "b"] || "a, b"
    }

    def "renderIterator pulls exactly one element and emits no separator when iterator yields a single element under unlimited budget"() {
        given: "an unlimited budget context plus a tracking iterator that yields exactly one element"
        def ctx = freshContext(Integer.MAX_VALUE)
        int nextCalls = 0
        int hasNextCalls = 0
        def iter = new Iterator<String>() {
            private boolean yielded = false
            boolean hasNext() {
                hasNextCalls++
                return !yielded
            }
            String next() {
                nextCalls++
                yielded = true
                return "only"
            }
        }

        when:
        CollectionRenderer.renderIterator(ctx, iter)

        then: "exactly one next() call was issued — the while-loop body was never entered"
        nextCalls == 1

        and: "the iterator was probed exactly twice: once before the first append and once at the top of the while-loop — the second probe returned false and broke out"
        hasNextCalls == 2

        and: "the output is the single element verbatim"
        ctx.builder.toString() == "only"
    }

    def "renderIterator stops pulling iterator elements at each distinct rejection branch"() {
        given:
        def ctx = freshContext(budget)
        int nextCallCount = 0
        def iter = new Iterator<String>() {
            private final List<String> data = ["a", "b", "c"]
            private int index = 0
            boolean hasNext() { return index < data.size() }
            String next() {
                nextCallCount++
                return data[index++]
            }
        }

        when:
        CollectionRenderer.renderIterator(ctx, iter)

        then:
        nextCallCount == expectedCalls

        where: "shared LOOP_REJECTION_GRID pins each rejection point — same semantics as the renderRandomAccessList twin: budget=0 → first appendObjectTo rejected; budget=1 → prependSeparator rejected when gap<2; budget=3 → second appendObjectTo triggers truncation"
        [budget, expectedCalls] << LOOP_REJECTION_GRID
    }
}
