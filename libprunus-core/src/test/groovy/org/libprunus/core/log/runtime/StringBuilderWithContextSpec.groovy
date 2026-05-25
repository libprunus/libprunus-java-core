package org.libprunus.core.log.runtime

import java.nio.CharBuffer
import org.slf4j.spi.LoggingEventBuilder
import spock.lang.Specification

class StringBuilderWithContextSpec extends Specification {

    def "constructor throws NullPointerException with diagnostic message when backing builder is null"() {
        when:
        new StringBuilderWithContext(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "builder must not be null"
    }

    def "setMaxMessageLength throws IllegalArgumentException when the requested length is negative and leaves the backing builder untouched"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())

        when:
        context.setMaxMessageLength(-1)

        then:
        thrown(IllegalArgumentException)
        context.builder.toString() == ""
    }

    def "setMaxMessageLength updates the active budget so a subsequent append respects the new bound on its first call"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(3)

        when:
        boolean ok = context.append("abcdefg")

        then:
        !ok
        context.builder.toString() == "..."
        context.isTruncated()
    }

    def "setMaxMessageLength below current builder length truncates in place via the render-time ellipsis algorithm and latches truncated"() {
        given: "a context whose builder already holds 10 chars under a generous budget"
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(256)
        context.builder.append("abcdefghij")

        when:
        context.setMaxMessageLength(newBudget)

        then: "the strict length bound is restored at the externally observable return point"
        context.builder.length() <= newBudget

        and: "the in-place truncation produced the exact ellipsis-trimmed form the render path would produce for the same budget"
        context.builder.toString() == expectedBuffer

        and: "the truncation latch is now set — subsequent appends will be no-ops"
        context.isTruncated()

        and: "this is value-level truncation, not an SOE or MAX_DEPTH audit cut — neither marker leaks into the buffer"
        !context.builder.toString().contains(StringBuilderWithContext.STACK_OVERFLOW_MARKER)
        !context.builder.toString().contains(StringBuilderWithContext.MAX_DEPTH_MARKER)

        where:
        newBudget || expectedBuffer
        5         || "ab..."
        2         || ".."
        0         || ""
    }

    def "setMaxMessageLength at-or-above current builder length leaves the buffer and the truncated latch unchanged"() {
        given: "a context holding 6 chars under a generous budget"
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(256)
        context.builder.append("abcdef")
        def bufferBefore = context.builder.toString()

        when:
        context.setMaxMessageLength(newBudget)

        then: "the buffer was not mutated — no shrink path fired because builder.length() <= newBudget"
        context.builder.toString() == bufferBefore

        and: "the truncated latch stays cleared — no value-level cut occurred"
        !context.isTruncated()

        where:
        newBudget << [6, 7, 256, 1048576]
    }

    def "toString returns the exact current builder contents including any prefix text"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder("seed:"))
        context.setMaxMessageLength(8)

        when:
        context.append("xyz")

        then: "toString returns the same content as builder.toString — the public toString is a pure pass-through"
        context.toString() == context.builder.toString()

        and: "the content includes the prefix plus the appended text up to the budget"
        context.toString() == "seed:xyz"
    }

    def "reset clears builder content and resets truncated state"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder("prefix"))
        context.setMaxMessageLength(3)
        context.append("long text that triggers truncation")

        when:
        context.reset(12)

        then:
        context.builder.toString() == ""
        !context.isTruncated()
    }

    def "reset clears auditMarkerAppended latch so a subsequent forceAppendAuditMarker still writes its marker"() {
        given: "a context whose audit-marker latch has been tripped by a previous forceAppendAuditMarker"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(32)
        context.forceAppendAuditMarker("...[SOE]")
        assert context.isTruncated()

        when:
        context.reset(32)
        context.forceAppendAuditMarker("...[MAX_DEPTH]")

        then: "the new marker was written — the auditMarkerAppended latch was released by reset"
        builder.toString() == "...[MAX_DEPTH]"

        and: "the new truncation cycle re-latched truncated"
        context.isTruncated()

        and: "the previous SOE marker did not leak into the new render — reset cleared the buffer"
        !builder.toString().contains("...[SOE]")
    }

    def "reset rewinds renderDepth so the MAX_RENDER_DEPTH budget is reusable in the next render cycle"() {
        given: "a context whose depth has been saturated at MAX_RENDER_DEPTH"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }
        assert !context.enterRenderDepth()

        when:
        context.reset(64)

        then: "a fresh enterRenderDepth still succeeds — renderDepth was rewound to 0"
        context.enterRenderDepth()

        and: "the MAX_DEPTH marker that was written by the saturating enterRenderDepth call did not survive reset"
        !builder.toString().contains("...[MAX_DEPTH]")

        and: "the truncated flag is no longer set after reset"
        !context.isTruncated()
    }

    def "enterRenderDepth and exitRenderDepth pair correctly so MAX_RENDER_DEPTH stays a moving budget across nested frames"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(64)

        when: "two frames are entered, one is exited, then a third is entered to confirm the budget is moving rather than fixed"
        boolean a1 = context.enterRenderDepth()
        boolean a2 = context.enterRenderDepth()
        context.exitRenderDepth()
        boolean a3 = context.enterRenderDepth()

        then: "all three enter attempts succeeded — exitRenderDepth made the second slot available again"
        a1
        a2
        a3

        and: "no truncation was tripped during these balanced enter/exit operations"
        !context.isTruncated()
    }

    def "enterRenderDepth writes MAX_DEPTH_MARKER and returns false at the MAX_RENDER_DEPTH boundary; truncated latches"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }

        when:
        boolean accepted = context.enterRenderDepth()

        then:
        !accepted
        builder.toString() == "...[MAX_DEPTH]"
        context.isTruncated()

        and:
        !context.append("ignored")
        builder.toString() == "...[MAX_DEPTH]"
    }

    def "enterRenderDepth invoked after MAX_RENDER_DEPTH already tripped does not duplicate MAX_DEPTH marker and keeps truncated latched"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        StringBuilderWithContext.MAX_RENDER_DEPTH.times { context.enterRenderDepth() }
        assert !context.enterRenderDepth()
        assert context.isTruncated()

        when:
        boolean accepted = context.enterRenderDepth()

        then:
        !accepted
        context.isTruncated()
        builder.toString() == "...[MAX_DEPTH]"
        builder.toString().findAll(/\Q...[MAX_DEPTH]\E/).size() == 1
    }

    def "exitRenderDepth decrements renderDepth even when context is already truncated and the decrement leaves no observable residue across reset"() {
        given: "a context that has entered one frame and is then latched truncated by an unrelated audit-marker path"
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(32)
        context.enterRenderDepth()
        int depthBefore = context.@renderDepth
        context.forceAppendAuditMarker("...[SOE]")
        assert context.isTruncated()

        when:
        context.exitRenderDepth()

        then: "renderDepth is decremented unconditionally — the truncated latch does not gate exitRenderDepth"
        context.@renderDepth == depthBefore - 1

        and: "the truncation latch is still set — exitRenderDepth did not clear it"
        context.isTruncated()

        when: "a reset is performed and a fresh enter is attempted"
        context.reset(32)

        then: "renderDepth has been rewound to zero — the post-truncate decrement left no residue across reset"
        context.@renderDepth == 0
        !context.isTruncated()
    }

    def "enterRenderDepth returns false immediately when context is already truncated by a non-depth path and does not advance renderDepth"() {
        given: "a context truncated via an audit marker unrelated to render depth (Q10 short-circuit target)"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(32)
        context.forceAppendAuditMarker("...[SOE]")
        assert context.isTruncated()
        int depthBefore = context.@renderDepth
        String contentBefore = builder.toString()

        when:
        boolean accepted = context.enterRenderDepth()

        then: "the Q10 truncated short-circuit returns false before the depth check or any audit-marker dispatch"
        !accepted

        and: "renderDepth was not advanced — the increment branch was skipped entirely"
        context.@renderDepth == depthBefore

        and: "no second audit marker was written — the builder content is identical to before the call"
        builder.toString() == contentBefore

        and: "the existing SOE marker was preserved (first-wins) and no MAX_DEPTH marker leaked in"
        !builder.toString().contains("MAX_DEPTH")
    }

    def "render rethrows non-StackOverflowError Error subtype raised by a Loggable element and leaves the builder empty"() {
        given:
        def builder = new StringBuilder()
        Loggable fatalLoggable = { StringBuilderWithContext ctx ->
            throw new OutOfMemoryError("heap")
        } as Loggable

        when:
        new StringBuilderWithContext(builder).tap { setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength()) }.render(fatalLoggable)

        then:
        def ex = thrown(OutOfMemoryError)
        ex.message == "heap"
        builder.toString().isEmpty()
    }

    def "render rethrows a non-StackOverflowError Error subtype other than OutOfMemoryError unchanged when raised by a Loggable element so handleRenderError is locked on the generic Error branch, not on OOM specifically"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength())
        def fatal = new InternalError("vm-internal")
        Loggable fatalLoggable = { StringBuilderWithContext ignored ->
            throw fatal
        } as Loggable

        when:
        context.render(fatalLoggable)

        then: "handleRenderError rethrows the exact instance because it is an Error and not a StackOverflowError"
        def ex = thrown(InternalError)
        ex.is(fatal)

        and: "no partial content leaked into the builder before the rethrow and no truncation latch was set"
        builder.toString().isEmpty()
        !context.isTruncated()
    }

    def "render absorbs a RuntimeException thrown from a Loggable and emits the typed throwable marker without rethrow"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        Loggable failing = { StringBuilderWithContext ignored ->
            throw new IllegalStateException("boom")
        } as Loggable

        when:
        context.render(failing)

        then: "the RuntimeException is absorbed at the render(Object) entry — not rethrown to the caller"
        noExceptionThrown()

        and: "the typed throwable marker is the entire output"
        builder.toString() == "...[java.lang.IllegalStateException]"

        and: "appendThrowableFallback did not by itself latch the truncated flag — the budget comfortably fit the marker"
        !context.isTruncated()
    }

    def "render preserves builder prefix before objectStartLength when CollectionRenderer forces CME audit marker via forceAppendAuditMarker"() {
        given:
        def prefix = "pre:\uD83D"
        def builder = new StringBuilder(prefix)
        def objectStart = builder.length()
        def failingCollection = new AbstractCollection<Object>() {
            @Override
            Iterator<Object> iterator() {
                throw new ConcurrentModificationException("cme")
            }

            @Override
            int size() {
                return 1
            }

            @Override
            boolean isEmpty() {
                return false
            }
        }

        when:
        new StringBuilderWithContext(builder).tap { setMaxMessageLength(objectStart + LogRuntime.getGlobalMaxMessageLength()) }.render(failingCollection)

        then:
        builder.toString().startsWith(prefix)
        builder.substring(0, objectStart) == prefix
        builder.toString().contains("...[CME]")
    }

    def setupSpec() {
        LogRuntimeTestSupport.resetBinding()
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 512 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        })
    }

    def "appendObjectTo routes to the correct renderer based on value type"() {
        given:
        def builder = new StringBuilder()

        when:
        boolean result = appendObjectTo(builder, value, -1, 0)

        then:
        result == expectedResult
        builder.toString() == expectedContent

        where:
        value                       || expectedResult | expectedContent
        null                        || true           | "null"
        "test"                      || true           | "test"
        123                         || true           | "123"
        true                        || true           | "true"
        Thread.State.NEW            || true           | "NEW"
        new StringBuilder("hello")  || true           | "hello"
        new BigDecimal("1.5")       || true           | "1.5"
        new int[]{1}                || true           | "[1]"
        new Object[]{}              || true           | "[]"
        new ArrayList<>()           || true           | "[]"
        new HashMap<>()             || true           | "{}"
        new boolean[0]              || true           | "[]"
        new byte[0]                 || true           | "[]"
        new char[0]                 || true           | "[]"
        new short[0]                || true           | "[]"
        new int[0]                  || true           | "[]"
        new long[0]                 || true           | "[]"
        new float[0]                || true           | "[]"
        new double[0]               || true           | "[]"
        new LinkedList<>()          || true           | "[]"
    }

    def "appendObjectTo dispatches a whitelisted non-Number type via NUMBER_OR_WHITELIST_RENDERER using String.valueOf"() {
        given:
        LogRuntimeTestSupport.resetBinding()
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 512 }
            @Override boolean isWhitelisted(Class<?> type) { return type == WhitelistedTarget }
        })
        def builder = new StringBuilder()

        when:
        appendObjectTo(builder, new WhitelistedTarget("hello"), -1, 0)

        then:
        builder.toString() == "WL[hello]"
        !(builder.toString() ==~ /.*WhitelistedTarget@[0-9a-f]+/)

        cleanup:
        LogRuntimeTestSupport.resetBinding()
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 512 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        })
    }

    def "appendObjectTo appends identity string for unknown plain object type"() {
        given:
        def builder = new StringBuilder()
        def value = new Object()

        when:
        boolean result = appendObjectTo(builder, value, -1, 0)

        then:
        result
        builder.toString() ==~ /java\.lang\.Object@[0-9a-f]+/
    }

    def "appendObjectTo renders Class object with 'class ' prefix via exact map entry"() {
        expect:
        render { b -> appendObjectTo(b, String.class, -1, 0) } == "class java.lang.String"
    }

    def "appendObjectTo writes partial output for Class value when budget cannot fit the 'class ' prefix"() {
        given:
        def builder = new StringBuilder()

        when:
        appendObjectTo(builder, String.class, 4, 0)

        then:
        builder.toString() == "c..."
    }

    def "appendObjectTo checks null before depth so null is always rendered even when depth is exceeded"() {
        given:
        def builder = new StringBuilder()

        when:
        boolean result = appendObjectTo(builder, null, -1, 0)

        then:
        result
        builder.toString() == "null"
    }

    def "appendObjectTo returns false and triggers truncation when budget is consumed"() {
        given:
        def builder = new StringBuilder("hello")
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(5)

        when:
        boolean result = context.appendObjectTo("more content")

        then:
        !result
        context.isTruncated()
    }

    def "appendObjectTo short-circuits before renderer dispatch when context is already truncated"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(0)
        context.append("x")
        assert context.isTruncated()
        int lengthBefore = builder.length()
        int[] callCount = [0]
        Loggable probe = { StringBuilderWithContext ignored ->
            callCount[0]++
            throw new IllegalStateException("renderer must not run when context is truncated")
        } as Loggable

        when:
        boolean result = context.appendObjectTo(probe)

        then:
        !result
        callCount[0] == 0
        builder.length() == lengthBefore
        context.isTruncated()
    }

    def "appendObjectTo routes Enum subclass with method body through ENUM_RENDERER yielding the declared name not the anonymous subclass identity"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)

        when:
        boolean result = context.appendObjectTo(EnumWithMethodBody.A)

        then:
        result
        builder.toString() == "A"
        !builder.toString().contains("\$")
        !builder.toString().contains("@")
    }

    def "appendObjectTo routes plain CharSequence (CharBuffer) through CHAR_SEQUENCE_RENDERER preserving character order"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        def buf = CharBuffer.wrap("hello")

        when:
        boolean result = context.appendObjectTo(buf)

        then:
        result
        builder.toString() == "hello"
        !context.isTruncated()
    }

    def "appendObjectTo renders non-empty container shapes (primitive int[], boxed Object[], List, LinkedList, single-entry Map) under unlimited budget"() {
        given:
        def builder = new StringBuilder()

        when:
        boolean result = appendObjectTo(builder, value, -1, 0)

        then:
        result
        builder.toString() == expected

        where:
        value                                                                            | expected
        [1, 2] as int[]                                                                  | "[1, 2]"
        ["a"] as Object[]                                                                | "[a]"
        [1, 2, 3]                                                                        | "[1, 2, 3]"
        new LinkedList<>([1, 2])                                                         | "[1, 2]"
        [1, 2, 3] as int[]                                                               | "[1, 2, 3]"
        [Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3)] as Object[]         | "[1, 2, 3]"
        [k: 1]                                                                           | "{k=1}"
    }

    def "appendObjectTo for a String value yields the same buffer content and result flag as a direct append(String) at the budget edge so the fast-path is equivalent to the typed overload"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(budget)

        when:
        boolean accepted = context.appendObjectTo(value)

        then: "appendObjectTo(String) matches the direct append(String) outcome on this budget"
        accepted == expectedAccepted
        context.builder.toString() == expectedContent
        context.isTruncated() == !expectedAccepted

        where:
        value  | budget || expectedAccepted | expectedContent
        "abcd" | 4      || true             | "abcd"
        "abcd" | 3      || false            | "..."
    }

    def "appendObjectTo renders scalar siblings of nested containers normally"() {
        given:
        def innerList = [2, 3]
        def mixed = [Integer.valueOf(1), innerList, "hello"]

        when:
        def result = render { b -> appendObjectTo(b, mixed, -1, 0) }

        then:
        result == "[1, [2, 3], hello]"
    }

    def "appendObjectTo renders Object array with mixed scalars and containers"() {
        given:
        Object[] mixed = [[1, 2] as int[], "x", Integer.valueOf(42)]

        when:
        def result = render { b -> appendObjectTo(b, mixed, -1, 0) }

        then:
        result == "[[1, 2], x, 42]"
    }

    def "appendObjectTo renders Map with mixed scalars and containers"() {
        given:
        def mixed = new LinkedHashMap<String, Object>()
        mixed.put("a", ["inner": 1])
        mixed.put("b", 42)

        when:
        def result = render { b -> appendObjectTo(b, mixed, -1, 0) }

        then:
        result == "{a={inner=1}, b=42}"
    }

    def "prependSeparator appends ', ' when gap >= 2 and triggers truncation suffix when gap < 2, latching truncated on every overflow row"() {
        given:
        def builder = new StringBuilder(initial)
        def context = contextOf(builder, objectBudgetLimit, 0)

        when:
        boolean result = context.prependSeparator()

        then:
        result == expectedResult
        builder.toString() == expectedContent

        and: "the truncation latch agrees with the false-result outcome on every row"
        context.isTruncated() == !expectedResult

        where:
        initial | objectBudgetLimit || expectedResult | expectedContent
        "abc"   | 4                 || false          | "a..."
        "abc"   | 5                 || true           | "abc, "
        "x"     | 2                 || false          | ".."
        ""      | -1                || true           | ", "
        "xy"    | 2                 || false          | ".."
    }

    def "appendThrowableFallback appends a typed marker for non-null throwables and is a no-op for null input"() {
        given:
        def context = contextOf(new StringBuilder(), 64, 0)

        when:
        context.appendThrowableFallback(input)

        then:
        context.builder.toString() == expectedContent
        !context.isTruncated()

        where:
        input                                  || expectedContent
        new IllegalStateException("boom")      || "...[java.lang.IllegalStateException]"
        null                                   || ""
    }

    def "appendThrowableFallback is a no-op when the context is already truncated"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(0)
        context.append("x")
        assert context.isTruncated()
        int lengthBefore = builder.length()

        when:
        context.appendThrowableFallback(new IllegalStateException("boom"))

        then:
        builder.length() == lengthBefore
        context.isTruncated()
    }

    def "appendThrowableFallback writes STACK_OVERFLOW_MARKER and latches truncation when throwable is StackOverflowError"() {
        given:
        def context = contextOf(new StringBuilder(), 64, 0)

        when:
        context.appendThrowableFallback(new StackOverflowError("deep"))

        then:
        context.builder.toString() == "...[SOE]"
        context.isTruncated()
    }

    def "appendThrowableFallback skips both the class-name and the closing bracket writes when the leading '...[' append already returns false at zero remaining budget"() {
        given: "a context whose builder length exactly equals maxMessageLength — allowed is 0 at the appendThrowableFallback entry"
        def builder = new StringBuilder("hello")
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(5)

        when:
        context.appendThrowableFallback(new RuntimeException("boom"))

        then: "the leading '...[' append fired triggerTruncationBase at zero allowed; class-name and closing bracket were skipped via the if (append('...[')) false branch"
        builder.length() <= 5
        context.isTruncated()

        and: "no class-name character or closing bracket leaked through the false-branch"
        !builder.toString().contains("]")
        !builder.toString().contains("RuntimeException")
        !builder.toString().contains("j")
    }

    def "appendThrowableFallback degrades through suffix-only, class-name-cut, and pure-dots forms as remaining budget shrinks below the full type-name length"() {
        given: "a context whose budget is tight enough to force the truncation-suffix degradation path"
        def builder = new StringBuilder(initial)
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(budget)

        when:
        context.appendThrowableFallback(new RuntimeException("boom"))

        then: "the buffer matches the exact degraded form for this budget tier"
        builder.toString() == expectedContent
        builder.length() == expectedLength

        and: "the truncation latch is set on every degradation path"
        context.isTruncated()

        and: "no class-name character or closing bracket leaked when the budget could not hold them"
        !builder.toString().contains("RuntimeException")
        !builder.toString().contains("]")

        where: "remaining budget shrinks across the three degradation tiers identified in the source"
        initial | budget || expectedContent | expectedLength
        "hello" | 7      || "hell..."       | 7
        ""      | 8      || "...[j..."      | 8
        ""      | 5      || "....."         | 5
    }

    def "append(char) emits the six-character escape for a high surrogate when remaining budget equals exactly 6"() {
        given: "a fresh context whose remaining budget is the exact 6-character minimum required by appendSurrogateEscape"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(6)

        when:
        boolean accepted = context.append((char) 0xD83D)

        then: "the escape was admitted — boundary equality with the >=6 guard passes"
        accepted

        and: "exactly six output characters were emitted in the canonical backslash-u-hex form"
        builder.length() == 6
        builder.charAt(0) == ((char) 0x5C)
        builder.charAt(1) == ((char) 0x75)
        builder.charAt(2) == ((char) 0x44)
        builder.charAt(3) == ((char) 0x38)
        builder.charAt(4) == ((char) 0x33)
        builder.charAt(5) == ((char) 0x44)

        and: "no truncation latch was set — the budget was met exactly"
        !context.isTruncated()
    }

    def "appendText retreats cut point to avoid leaving an orphaned high surrogate in the output"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())

        when:
        appendTextWithBudget(context, "hell\uD83DXend", 0, 8)

        then:
        context.builder.toString() == "hell..."
        !Character.isHighSurrogate(context.builder.charAt(context.builder.length() - 1 - StringBuilderWithContext.TRUNCATION_SUFFIX_LENGTH))
    }

    def "appendText normalizes null to literal when allowed budget is positive"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())

        when:
        def result = appendTextWithBudget(context, null, 0, 10)

        then:
        result
        context.builder.toString() == "null"
    }

    def "appendText retreats one char when cut falls inside a surrogate pair"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())

        when:
        appendTextWithBudget(context, "a\uD83D\uDE00bcd", 0, 5)

        then:
        context.builder.toString() == "a..."
    }

    def "appendText truncates surrogate pair safely when pair straddles cut point"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())

        when:
        def result = appendTextWithBudget(context, "ab\uD83D\uDE00cd", 0, 5)

        then:
        !result
        context.builder.toString() == "ab..."
    }

    def "appendText appends full text when length fits within budget"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())

        when:
        def result = appendTextWithBudget(context, "hello", 0, 10)

        then:
        result
        context.builder.toString() == "hello"
    }

    def "appendText returns false immediately and triggers truncation when allowed budget is zero for non-null text"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder("log: "))

        when:
        boolean result = appendTextWithBudget(context, "ignored", 5, 0)

        then:
        !result
        context.builder.toString() == "lo..."
        context.isTruncated()
    }

    def "appendCharSequence returns false and triggers truncation when allowed budget is zero"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder("log: "))

        when:
        boolean result = appendTextWithBudget(context, null as CharSequence, 5, 0)

        then:
        !result
        context.builder.toString() == "lo..."
        context.isTruncated()
    }

    def "appendCharSequence normalizes null to literal when allowed budget is positive"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder())

        when:
        def result = appendTextWithBudget(context, null as CharSequence, 0, 10)

        then:
        result
        context.builder.toString() == "null"
    }

    def "appendWithBudget for int truncates with suffix when value overflows a partial budget starting at non-zero objectStartLength"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder("id: "))

        when:
        boolean result = appendPrimitiveWithBudget(context, Integer.MAX_VALUE, 4, 5)

        then:
        !result
        context.builder.toString() == "id: ..."
    }

    def "appendWithBudget for int preserves truncate-equivalent suffix semantics when tiny remaining budget overflows"() {
        given:
        def context = new StringBuilderWithContext(new StringBuilder("pre:abcd"))

        when:
        boolean result = appendPrimitiveWithBudget(context, 1000, 4, 6)

        then:
        !result
        context.builder.toString() == "pre:abc..."
    }

    def "every append overload that returns boolean returns false immediately when the context is already truncated and leaves the buffer unchanged"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(0)
        context.append("x")
        assert context.isTruncated()
        int lengthBefore = builder.length()

        when:
        boolean result = action.call(context)

        then: "the short-circuit at the truncated guard returns false without touching the buffer"
        !result
        builder.length() == lengthBefore
        context.isTruncated()

        where: "every public/package-private boolean-returning append overload + appendObjectTo + prependSeparator"
        action << [
                { StringBuilderWithContext c -> c.append(true) },
                { StringBuilderWithContext c -> c.append((byte) 1) },
                { StringBuilderWithContext c -> c.append((char) 'a') },
                { StringBuilderWithContext c -> c.append((short) 1) },
                { StringBuilderWithContext c -> c.append((int) 1) },
                { StringBuilderWithContext c -> c.append((long) 1L) },
                { StringBuilderWithContext c -> c.append((float) 1.0f) },
                { StringBuilderWithContext c -> c.append((double) 1.0d) },
                { StringBuilderWithContext c -> c.append("text") },
                { StringBuilderWithContext c -> c.append((CharSequence) "seq") },
                { StringBuilderWithContext c -> c.appendObjectTo("obj") },
                { StringBuilderWithContext c -> c.prependSeparator() },
        ]
    }

    def "void-returning render and forceAppendAuditMarker also leave the buffer unchanged when the context is already truncated"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(0)
        context.append("x")
        assert context.isTruncated()
        int lengthBefore = builder.length()

        when:
        action.call(context)

        then:
        builder.length() == lengthBefore
        context.isTruncated()

        where:
        action << [
                { StringBuilderWithContext c -> c.render("noop") },
                { StringBuilderWithContext c -> c.forceAppendAuditMarker("[X]") },
        ]
    }

    def "appendFallbackString rethrows a non-SOE Error raised by value.toString() without writing partial content"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength())

        when:
        context.appendFallbackString(new OomNumber())

        then:
        def ex = thrown(OutOfMemoryError)
        ex.message == "oom-in-toString"
        builder.toString().isEmpty()
    }

    def "appendFallbackString catches non-Error from value.toString() and routes through appendThrowableFallback"() {
        given:
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(LogRuntime.getGlobalMaxMessageLength())

        when:
        boolean result = context.appendFallbackString(new FailingNumber())

        then:
        !result
        builder.toString() == "...[java.lang.RuntimeException]"
    }

    def "forceAppendAuditMarker handles truncation marker replacement semantics"() {
        given: "a builder pre-loaded with seven UTF-16 chars (including a surrogate pair) and a context whose budget is injected directly so the strict shrink-truncate path in setMaxMessageLength does not pre-empt the marker logic under test"
        def builder = new StringBuilder("AB\uD83D\uDE00XYZ")
        def context = new StringBuilderWithContext(builder)
        context.@maxMessageLength = budget

        when:
        context.forceAppendAuditMarker("...")

        then:
        builder.toString() == expected

        where:
        budget || expected
        2      || ".."
        3      || "..."
        6      || "AB..."
        10     || "AB\uD83D\uDE00XYZ..."
    }

    def "forceAppendAuditMarker writes nothing yet still latches truncated and the audit-marker latch when maxMessageLength is zero and the marker is non-empty"() {
        given: "a fresh context with a non-empty marker but zero budget — cutPoint and toAppend both collapse to 0"
        def context = new StringBuilderWithContext(new StringBuilder())
        context.setMaxMessageLength(0)

        when:
        context.forceAppendAuditMarker("...[SOE]")

        then: "the marker append is suppressed by the toAppend > 0 guard, but the audit-marker latch still trips"
        context.builder.length() == 0
        context.isTruncated()

        when: "a second forceAppendAuditMarker with a different marker is invoked"
        context.forceAppendAuditMarker("...[MAX_DEPTH]")

        then: "the first-wins audit-marker latch suppresses the second write — buffer remains empty"
        context.builder.length() == 0
        context.isTruncated()
    }

    def "forceAppendAuditMarker with empty marker leaves builder unchanged but still latches truncated and auditMarkerAppended"() {
        given:
        def builder = new StringBuilder("seed")
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(8)

        when:
        context.forceAppendAuditMarker("")

        then:
        builder.toString() == "seed"
        context.isTruncated()

        when:
        context.forceAppendAuditMarker("...[SOE]")

        then:
        builder.toString() == "seed"
        context.isTruncated()
        !builder.toString().contains("SOE")
    }

    def "markRenderTruncation appends the render-truncation marker when budget permits"() {
        given:
        def builder = new StringBuilder("User(id=42, name=hello")
        def context = contentContextOf(builder, 0, 64)

        when:
        context.markRenderTruncation()

        then:
        builder.toString() == "User(id=42, name=hello...[TRUNCATED])"
        context.isTruncated()
    }

    def "markRenderTruncation overrides a previously-appended bare ... truncation suffix without exceeding budget"() {
        given:
        def builder = new StringBuilder("User(id=42, name=he...")
        def context = contentContextOf(builder, 0, 22)

        when:
        context.markRenderTruncation()

        then:
        builder.length() <= 22
        builder.toString().endsWith("...[TRUNCATED])")
        context.isTruncated()
    }

    def "audit marker latch is first-wins across forceAppendAuditMarker and markRenderTruncation"() {
        given:
        def builder = new StringBuilder("prefix")
        def context = contentContextOf(builder, 0, 32)
        firstCall(context)
        def afterFirst = builder.toString()

        when:
        secondCall(context)

        then:
        builder.toString() == afterFirst
        context.isTruncated()

        where:
        firstCall << [
            { it.forceAppendAuditMarker("...[SOE]") },
            { it.markRenderTruncation() },
            { it.forceAppendAuditMarker("...[SOE]") },
        ]
        secondCall << [
            { it.forceAppendAuditMarker("...[MAX_DEPTH]") },
            { it.markRenderTruncation() },
            { it.markRenderTruncation() },
        ]
    }

    def "appendArrayTo with unlimited budget renders all elements regardless of accumulated length"() {
        given:
        int[] array = (1..50) as int[]

        when:
        def result = render { b -> contextOf(b, -1, 0).appendArrayTo(array) }

        then:
        result == "[" + (1..50).join(", ") + "]"
    }

    def "appendArrayTo with unlimited budget preserves exact int and long array formatting for signed edge values"() {
        expect:
        render { b -> appendFn(b) } == expected

        where:
        [appendFn, expected] << [
            [{ b -> contextOf(b, -1, 0).appendArrayTo([Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE] as int[]) }, "[-2147483648, -1, 0, 2147483647]"],
            [{ b -> contextOf(b, -1, 0).appendArrayTo([Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE] as long[]) }, "[-9223372036854775808, -1, 0, 9223372036854775807]"],
        ]
    }

    def "logAndRelease invokes event.log with the builder content and releases the context back to the pool exactly once"() {
        given:
        def event = Mock(LoggingEventBuilder)
        def context = StringBuilderPool.acquire()
        context.builder.append("test message")

        when:
        context.logAndRelease(event)

        then:
        1 * event.log("test message")
        0 * event._
    }

    def "logAndRelease releases the context back to the pool even when event.log throws"() {
        given:
        def event = Mock(LoggingEventBuilder)
        def context = StringBuilderPool.acquire()
        context.builder.append("failing message")
        event.log(_ as String) >> { throw new RuntimeException("log failure") }

        when:
        context.logAndRelease(event)

        then:
        thrown(RuntimeException)

        and:
        def reacquired = StringBuilderPool.acquire()
        reacquired.builder.length() == 0

        cleanup:
        StringBuilderPool.release(reacquired)
    }

    def "reportLoggingFailure does not throw for non-fatal throwable inputs spanning blank and present owner-method strings"() {
        when:
        StringBuilderWithContext.reportLoggingFailure(ownerAndMethod, throwable)

        then:
        noExceptionThrown()

        where:
        ownerAndMethod          | throwable
        ""                      | null
        "org.MyClass.myMethod"  | null
        "org.MyClass.myMethod"  | new RuntimeException("test")
    }

    def "reportLoggingFailure rethrows OutOfMemoryError synchronously to the caller"() {
        when:
        StringBuilderWithContext.reportLoggingFailure("org.MyClass.myMethod", new OutOfMemoryError())

        then:
        thrown(OutOfMemoryError)
    }

    def "reportLoggingFailure keeps reporting after a fatal throwable occurs inside reporter consume loop"() {
        given:
        def originalErr = System.err
        def captured = new java.io.ByteArrayOutputStream()
        def fatalErr = new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true) {
            @Override
            void println(String x) {
                throw new RuntimeException("err-fail")
            }
        }
        def reporter = LoggingFailureReporter.instance()
        reporter.@droppedCount.reset()
        reporter.@droppedCount.add(1L)
        reporter.@rateLimiter.set(0L)
        System.setErr(fatalErr)

        when:
        StringBuilderWithContext.reportLoggingFailure("Integration.fatalEntry", null)
        Thread.sleep(120)
        reporter.@rateLimiter.set(0L)
        System.setErr(new java.io.PrintStream(captured, true))
        StringBuilderWithContext.reportLoggingFailure("Integration.recoveredEntry", new RuntimeException("recovered-cause"))

        then:
        def deadline = System.nanoTime() + 2_000_000_000L
        while (!captured.toString().contains("Integration.recoveredEntry") && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        captured.toString().contains("Integration.recoveredEntry")

        cleanup:
        System.setErr(originalErr)
    }

    def "handleRenderFailure does not rethrow for non-fatal throwable types and tolerates null context"() {
        given:
        StringBuilderPool.@POOL.remove()
        def context = useContext ? StringBuilderPool.acquire() : null

        when:
        StringBuilderWithContext.handleRenderFailure("Owner.method", context, throwable)

        then:
        noExceptionThrown()

        where:
        useContext | throwable
        true       | new RuntimeException("render-fail")
        false      | new RuntimeException("render-fail")
        true       | new StackOverflowError()
    }

    def "handleRenderFailure rethrows OOM without reporting"() {
        given:
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        def oom = new OutOfMemoryError("heap")

        when:
        StringBuilderWithContext.handleRenderFailure("Owner.method", context, oom)

        then:
        def ex = thrown(OutOfMemoryError)
        ex.is(oom)
    }

    def "handleRenderFailure rethrows OOM unchanged when stringBuilder is null and skips the pool release branch"() {
        given:
        def oom = new OutOfMemoryError("heap")

        when:
        StringBuilderWithContext.handleRenderFailure("Owner.method", null, oom)

        then:
        def ex = thrown(OutOfMemoryError)
        ex.is(oom)
    }

    def "recoverToStringFallback returns string content for non-error throwable"() {
        given:
        def context = acquireIsolatedContextWith("prefix")
        def failure = new RuntimeException("render-failed")

        when:
        def rendered = StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, failure)

        then:
        rendered == "prefix"
    }

    def "recoverToStringFallback rethrows non-abort Error unchanged"() {
        given:
        def context = acquireIsolatedContextWith("prefix")
        def failure = new AssertionError("render-failed")

        when:
        StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, failure)

        then:
        def ex = thrown(AssertionError)
        ex.is(failure)
    }

    def "recoverToStringFallback releases the StringBuilderWithContext back to the thread-local pool on the non-error path"() {
        given:
        def context = acquireIsolatedContextWith("partial")
        def failure = new RuntimeException("upstream-render-failed")

        when:
        def rendered = StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, failure)
        def reacquired = StringBuilderPool.acquire()

        then:
        rendered == "partial"
        reacquired.is(context)
        reacquired.builder.length() == 0

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
    }

    def "recoverToStringFallback releases the StringBuilderWithContext back to the pool before rethrowing a non-abort Error"() {
        given:
        def context = acquireIsolatedContextWith("prefix")
        def failure = new AssertionError("render-failed")

        when:
        try {
            StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, failure)
        } catch (AssertionError ignored) {
            // expected — verified separately
        }
        def reacquired = StringBuilderPool.acquire()

        then:
        reacquired.is(context)
        reacquired.builder.length() == 0

        cleanup:
        StringBuilderPool.release(reacquired)
        StringBuilderPool.@POOL.remove()
    }

    def "recoverToStringFallback with null stringBuilder rethrows non-abort Error unchanged and returns empty string for non-Error throwable, with no release attempted in either branch"() {
        when:
        def rendered = null
        Throwable caught = null
        try {
            rendered = StringBuilderWithContext.recoverToStringFallback("Owner.toString", null, failure)
        } catch (Throwable t) {
            caught = t
        }

        then: "the rethrow-vs-return split follows the Error / non-Error classification"
        (expectedThrowType == null) == (caught == null)

        and: "on the Error branch, the exact original instance was rethrown with no suppressed accumulation"
        if (expectedThrowType != null) {
            assert caught.class == expectedThrowType
            assert caught.is(failure)
            assert caught.suppressed.length == 0
        }

        and: "on the non-Error branch, the fallback string is the empty-string sentinel produced for null builder"
        if (expectedThrowType == null) {
            assert rendered == expectedRendered
        }

        where:
        failure                                       || expectedRendered | expectedThrowType
        new AssertionError("render-failed")           || null             | AssertionError
        new RuntimeException("render-failed")         || ""               | null
    }

    def "recoverToStringFallback returns builder content without reporting when throwable is null"() {
        given:
        def context = acquireIsolatedContextWith("complete")

        when:
        def rendered = StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, null)

        then:
        rendered == "complete"
        noExceptionThrown()

        cleanup:
        StringBuilderPool.@POOL.remove()
    }

    def "recoverToStringFallback treats StackOverflowError throwable as non-fatal and returns builder content"() {
        given:
        def context = acquireIsolatedContextWith("partial")
        def failure = new StackOverflowError("render-failed")

        when:
        def rendered = StringBuilderWithContext.recoverToStringFallback("Owner.toString", context, failure)

        then:
        rendered == "partial"
        noExceptionThrown()

        cleanup:
        StringBuilderPool.@POOL.remove()
    }

    private static boolean appendTextWithBudget(
            StringBuilderWithContext context, String text, int objectStartLength, int objectBudgetLimit) {
        context.setMaxMessageLength(safeMaxLength(objectStartLength, objectBudgetLimit))
        context.append(text)
    }

    private static boolean appendTextWithBudget(
            StringBuilderWithContext context, CharSequence value, int objectStartLength, int objectBudgetLimit) {
        context.setMaxMessageLength(safeMaxLength(objectStartLength, objectBudgetLimit))
        context.append(value)
    }

    private static boolean appendPrimitiveWithBudget(
            StringBuilderWithContext context, Object value, int objectStartLength, int objectBudgetLimit) {
        context.setMaxMessageLength(safeMaxLength(objectStartLength, objectBudgetLimit))
        if (value instanceof Boolean) { return context.append((boolean) value) }
        if (value instanceof Byte) { return context.append((byte) value) }
        if (value instanceof Character) { return context.append((char) value) }
        if (value instanceof Short) { return context.append((short) value) }
        if (value instanceof Integer) { return context.append((int) value) }
        if (value instanceof Long) { return context.append((long) value) }
        if (value instanceof Float) { return context.append((float) value) }
        if (value instanceof Double) { return context.append((double) value) }
        throw new IllegalArgumentException("Unsupported primitive wrapper: " + value)
    }

    private static int safeMaxLength(int objectStartLength, int objectBudgetLimit) {
        return objectBudgetLimit < 0 ? Integer.MAX_VALUE : objectStartLength + objectBudgetLimit
    }

    private static String render(Closure<?> call) {
        def builder = new StringBuilder()
        call(builder)
        builder.toString()
    }

    private static boolean appendObjectTo(
            StringBuilder builder,
            Object value,
            int maxObjectLength,
            int objectStartLength) {
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(safeMaxLength(objectStartLength, maxObjectLength))
        context.appendObjectTo(value)
    }

    private static StringBuilderWithContext contextOf(
            StringBuilder builder,
            int maxObjectLength,
            int objectStartLength) {
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(safeMaxLength(objectStartLength, maxObjectLength))
        context
    }

    private static StringBuilderWithContext contentContextOf(
            StringBuilder builder, int objectStartLength, int contentBudget) {
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(safeMaxLength(objectStartLength, contentBudget))
        context
    }

    private static StringBuilderWithContext acquireIsolatedContextWith(String content) {
        StringBuilderPool.@POOL.remove()
        def context = StringBuilderPool.acquire()
        if (content != null) {
            context.builder.append(content)
        }
        context
    }

    private static final class WhitelistedTarget {
        final String label
        WhitelistedTarget(String label) { this.label = label }
        @Override String toString() { return "WL[" + label + "]" }
    }

    private static final class OomNumber extends Number {
        @Override int intValue() { return 0 }
        @Override long longValue() { return 0L }
        @Override float floatValue() { return 0.0f }
        @Override double doubleValue() { return 0.0d }
        @Override String toString() { throw new OutOfMemoryError("oom-in-toString") }
    }

    private static final class FailingNumber extends Number {
        @Override int intValue() { return 0 }
        @Override long longValue() { return 0L }
        @Override float floatValue() { return 0.0f }
        @Override double doubleValue() { return 0.0d }
        @Override String toString() { throw new RuntimeException("boom") }
    }

    private static enum EnumWithMethodBody {
        A {
            @Override
            String describe() { return "alpha-body" }
        },
        B {
            @Override
            String describe() { return "beta-body" }
        }

        abstract String describe()
    }
}
