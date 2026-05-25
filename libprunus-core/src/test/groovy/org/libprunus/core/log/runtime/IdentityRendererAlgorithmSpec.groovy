package org.libprunus.core.log.runtime

import spock.lang.Specification

class IdentityRendererAlgorithmSpec extends Specification {

    private static StringBuilderWithContext freshContext(int maxLen) {
        def ctx = new StringBuilderWithContext(new StringBuilder())
        ctx.setMaxMessageLength(maxLen)
        ctx
    }

    def "render halts at each append boundary with exact deterministic truncated output across all target and fail-index combinations"() {
        given: "a context with budget calibrated to reject exactly the nth append call across the full 1..8 append pipeline"
        def className = targetValue.getClass().getName()
        def hex = Integer.toHexString(System.identityHashCode(targetValue))

        // Budget mapping: reject at call #failOnAppendIndex
        //   failAt=1 → maxLen=0      (className append fails immediately)
        //   failAt=k (k≥2) → maxLen=className.length()+(k-2)
        //     After (k-1) successful appends, builder = className + '@' + first(k-3) hex digits,
        //     and its length equals maxLen exactly, so the next append triggers truncation.
        def maxLen = failOnAppendIndex == 1 ? 0 : (className.length() + failOnAppendIndex - 2)

        // Exact expected output derived from triggerTruncationBase() arithmetic:
        //   triggerTruncationBase cuts to (maxLen-3) chars of the pre-cut buffer and appends "...".
        //   pre-cut buffer for failAt=k (k≥2) = className + '@' + first(k-3) hex digits
        def preCutBuffer = failOnAppendIndex == 1
            ? ""
            : className + (failOnAppendIndex >= 3 ? '@' : '') +
              (failOnAppendIndex >= 4 ? hex.substring(0, failOnAppendIndex - 3) : '')
        def expectedOutput = failOnAppendIndex == 1
            ? ""
            : preCutBuffer.substring(0, maxLen - 3) + "..."

        def builder = new StringBuilder()
        def ctx = new StringBuilderWithContext(builder)
        ctx.setMaxMessageLength(maxLen)

        when: "render is invoked with the budget-constrained context"
        IdentityRenderer.INSTANCE.render(ctx, targetValue)

        then: "context is truncated and the output exactly matches the string at the nth append boundary"
        ctx.isTruncated()
        builder.toString() == expectedOutput

        and: "the strict budget upper bound was never breached — output length never exceeds maxLen"
        builder.length() <= Math.max(maxLen, 0)

        where:
        [targetValue, failOnAppendIndex] << (
            [
                [new Object(), "TestString", 0, Long.MAX_VALUE, Collections.emptyList()],
                [1, 2, 3, 4, 5, 6, 7, 8]
            ].combinations()
        ).findAll { tv, fi ->
            // The fail-index is reachable only when the hex tail produces enough digits before
            // the failing append. For fi ≤ 2 the rejection lives in the className/'@' phase and is
            // independent of hex length; for fi ≥ 3 the renderer must attempt the (fi-2)th hex
            // digit, so the hex must hold at least (fi-2) characters.
            fi <= 2 || Integer.toHexString(System.identityHashCode(tv)).length() >= (fi - 2)
        }
    }

    def "render with budget equal to className length terminates with truncation suffix and never writes '@' or any hex digit"() {
        given: "a value of a known className with budget set exactly to the className length"
        def value = new Object()
        def className = value.getClass().getName()
        def builder = new StringBuilder()
        def ctx = new StringBuilderWithContext(builder)
        ctx.setMaxMessageLength(className.length())

        when:
        IdentityRenderer.INSTANCE.render(ctx, value)

        then: "the context is flagged truncated"
        ctx.isTruncated()

        and: "the output is exactly className.substring(0, className.length()-3) + '...' — derived from triggerTruncationBase rewind arithmetic"
        builder.toString() == className.substring(0, className.length() - 3) + "..."

        and: "the output is exactly className.length() characters long — strict budget honored"
        builder.length() == className.length()

        and: "no '@' leaked into the output — the second append was rejected before character emission"
        !builder.toString().contains("@")

        and: "no hex digit leaked into the output — the hex loop was never entered"
        !(builder.toString() ==~ /.*@[0-9a-f]+.*/)
    }

    def "render writes the exact canonical identity form using raw Class.getName() verbatim across diverse value types including nested and anonymous classes with unlimited budget"() {
        given:
        def builder = new StringBuilder()
        def ctx = new StringBuilderWithContext(builder)
        ctx.setMaxMessageLength(Integer.MAX_VALUE)
        def className = targetValue.getClass().getName()
        def hex = Integer.toHexString(System.identityHashCode(targetValue))

        when:
        IdentityRenderer.INSTANCE.render(ctx, targetValue)

        then:
        builder.toString() == className + '@' + hex
        builder.toString().startsWith(className)
        !builder.toString().substring(0, className.length()).contains("/")
        !ctx.isTruncated()

        where:
        targetValue << [
            new Object(),
            "TestString",
            0,
            Long.MAX_VALUE,
            Collections.emptyList(),
            new NestedStaticTarget(),
            new Object() {},
            Thread.currentThread()
        ]
    }

    static class NestedStaticTarget {
        // Empty marker — its very className contains '$' because it is a nested static class.
    }

    def "appendHashCodeHex emits exact boundary-driven digit sequence for hashCodes across the full leading-zero spectrum without leaving the context truncated under ample budget"() {
        given: "an ample-budget context that can never overflow during the hex emission"
        def ctx = freshContext(Integer.MAX_VALUE)

        when:
        IdentityRenderer.appendHashCodeHex(ctx, hashCode as int)

        then: "the emitted digit sequence matches the firstShift-driven expectation exactly"
        ctx.builder.toString() == expectedOutput

        and: "the emission is identical to Integer.toHexString — locking the project-layer contract IdentityRenderer extends to LogRuntime callers"
        ctx.builder.toString() == Integer.toHexString(hashCode as int)

        and: "no truncation latch was tripped — emission stayed inside budget"
        !ctx.isTruncated()

        where: "covers leadingZeros=32/28/27/0/3 — the four firstShift regions plus a typical mid-range hash, plus high-bit-1/middle-zero combinations for nibble decode correctness"
        hashCode             | expectedOutput || _
        0x00000000 as int    | "0"            || _
        0x0000000F as int    | "f"            || _
        0x00000010 as int    | "10"           || _
        0xFFFFFFFF as int    | "ffffffff"     || _
        0x12345678 as int    | "12345678"     || _
        0x10000000 as int    | "10000000"     || _
        0x80000000 as int    | "80000000"     || _
    }

    def "appendHashCodeHex stops at the truncation boundary and flips the truncated latch when the budget cuts mid-emission"() {
        given: "a context whose budget admits at most three raw hex chars before the truncation suffix kicks in"
        def ctx = freshContext(4)

        when: "emitting the eight-digit hash 0x12345678 — the fifth digit overflows and triggers truncation"
        IdentityRenderer.appendHashCodeHex(ctx, 0x12345678)

        then: "the output is rewound to one leading hex digit plus the truncation suffix — the strict budget is honored"
        ctx.builder.toString() == "1..."

        and: "the truncation latch is set — confirming the short-circuit in appendHashCodeHex's loop fired"
        ctx.isTruncated()

        and: "the builder length never exceeds the configured budget"
        ctx.builder.length() == 4
    }
}
