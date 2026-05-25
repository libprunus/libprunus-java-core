package org.libprunus.core.log.runtime

import spock.lang.Specification

class IdentityRendererSpec extends Specification {

    def "render appends class name followed by at-sign and trimmed hex identity hash code to context"() {
        given:
        def target = new Object()
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        def expectedHex = Integer.toHexString(System.identityHashCode(target))
        def expectedOutput = target.getClass().getName() + '@' + expectedHex

        when:
        IdentityRenderer.INSTANCE.render(context, target)

        then:
        builder.toString() == expectedOutput
        !context.isTruncated()

        and: "no audit/value-cut marker leaked into the buffer alongside the canonical identity form"
        !builder.toString().contains('...')

        and: "builder length is exactly className + '@' + hex — no extra bytes written"
        builder.length() == target.getClass().getName().length() + 1 + expectedHex.length()
    }

    def "render against an independent context including a previously-truncated one never bleeds state into a fresh unlimited-budget context"() {
        given:
        def victim = new Object()
        def firstCtx = new StringBuilderWithContext(new StringBuilder())
        firstCtx.setMaxMessageLength(firstCtxBudget)
        def freshCtx = new StringBuilderWithContext(new StringBuilder())
        freshCtx.setMaxMessageLength(Integer.MAX_VALUE)
        def expectedFreshOutput = victim.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(victim))

        when:
        IdentityRenderer.INSTANCE.render(firstCtx, victim)
        IdentityRenderer.INSTANCE.render(freshCtx, victim)

        then:
        freshCtx.builder.toString() == expectedFreshOutput
        !freshCtx.isTruncated()

        where:
        firstCtxBudget << [Integer.MAX_VALUE, 3]
    }

    def "render writes java.lang.String identity form when invoked directly with a String value bypassing the appendObjectTo early-return"() {
        given:
        def value = 'abc'
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        def expectedHex = Integer.toHexString(System.identityHashCode(value))

        when:
        IdentityRenderer.INSTANCE.render(context, value)

        then:
        builder.toString() == 'java.lang.String@' + expectedHex
        !context.isTruncated()

        and: "toString() / CharSequence path never engaged — payload 'abc' must not appear in the output"
        !builder.toString().contains('abc')
    }

}
