package org.libprunus.core.log.runtime

import spock.lang.Specification

class IdentityRendererIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "public render dispatches unknown object to IdentityRenderer producing identity suffix equal to Integer.toHexString(identityHashCode)"() {
        given:
        def value = new Object()
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)
        def expected = "${value.getClass().getName()}@${Integer.toHexString(System.identityHashCode(value))}"

        when:
        context.render(value)

        then:
        builder.toString() == expected
        !context.isTruncated()
    }

    def "public render keeps full IdentityRenderer payload when remaining budget exactly equals identity payload length"() {
        given:
        def value = new Object()
        def prefix = "prefix-"
        def expectedPayload = "${value.getClass().getName()}@${Integer.toHexString(System.identityHashCode(value))}"
        def builder = new StringBuilder(prefix)
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(prefix.length() + expectedPayload.length())

        when:
        context.render(value)

        then:
        builder.toString() == prefix + expectedPayload
        !context.isTruncated()
    }

    def "public render dispatches diverse fallback-eligible value shapes through IdentityRenderer and ignores their toString() override"() {
        given: "strict non-whitelist binding plus a comfortable-budget context"
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override
            int getMaxMessageLength() { return 256 }

            @Override
            boolean isWhitelisted(Class<?> type) { return false }
        })
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(256)

        when:
        context.render(value)

        then: "output matches canonical identity form — proving dispatch routed to IdentityRenderer"
        builder.toString() ==~ /\Q${value.getClass().getName()}\E@[0-9a-f]+/

        and: "toString() override never leaked into output"
        !builder.toString().contains(forbiddenToStringFragment)

        and: "no truncation under comfortable budget"
        !context.isTruncated()

        where:
        value                                    || forbiddenToStringFragment
        new PlainPojo("payload")                 || "payload"
        new ChattyToString()                     || "chatty"
        new ExceptionThrowingToString()          || "exception-from-toString"
    }

    static class PlainPojo {
        private final String payload

        PlainPojo(String payload) { this.payload = payload }

        @Override
        String toString() { return "PlainPojo{payload=" + payload + "}" }
    }

    static class ChattyToString {
        @Override
        String toString() { return "chatty-banner-that-must-not-leak-through-identity-renderer" }
    }

    static class ExceptionThrowingToString {
        @Override
        String toString() { throw new RuntimeException("exception-from-toString") }
    }
}
