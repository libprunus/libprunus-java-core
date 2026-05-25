package org.libprunus.core.log.runtime

import spock.lang.Specification

class RendererCacheBindingDecisionIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "RENDERER_CACHE honors the bound config whitelist routing first-encountered user types to fallback string renderer"() {
        given:
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 64 }
            @Override boolean isWhitelisted(Class<?> type) {
                return type == BindingWhitelistedSample
            }
        })
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(64)

        when:
        context.appendObjectTo(new BindingWhitelistedSample("v"))

        then:
        builder.toString() == "BindingWhitelistedSample{v}"
    }

    def "RENDERER_CACHE routes a non-whitelisted user type through IdentityRenderer regardless of toString()"() {
        given:
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 256 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        })
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(256)

        when:
        context.appendObjectTo(new NonWhitelistedSample("ignored"))

        then:
        builder.toString() ==~ /\Q${NonWhitelistedSample.name}\E@[0-9a-f]+/
    }

    def "RENDERER_CACHE caches first-encountered non-whitelist decision and survives a later rebind that whitelists the same type"() {
        given:
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 256 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        })
        def firstBuilder = new StringBuilder()
        def firstContext = new StringBuilderWithContext(firstBuilder)
        firstContext.setMaxMessageLength(256)

        when:
        firstContext.appendObjectTo(new StickyFirstNonWhitelisted("seed"))

        then:
        firstBuilder.toString() ==~ /\Q${StickyFirstNonWhitelisted.name}\E@[0-9a-f]+/

        when:
        LogRuntimeTestSupport.resetBinding()
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 256 }
            @Override boolean isWhitelisted(Class<?> type) { return type == StickyFirstNonWhitelisted }
        })
        def secondBuilder = new StringBuilder()
        def secondContext = new StringBuilderWithContext(secondBuilder)
        secondContext.setMaxMessageLength(256)
        secondContext.appendObjectTo(new StickyFirstNonWhitelisted("after"))

        then:
        secondBuilder.toString() ==~ /\Q${StickyFirstNonWhitelisted.name}\E@[0-9a-f]+/
        !secondBuilder.toString().contains("should-not-be-used:after")
    }

    def "RENDERER_CACHE caches first-encountered whitelist decision and survives a later rebind that withdraws the whitelist"() {
        given:
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 64 }
            @Override boolean isWhitelisted(Class<?> type) { return type == StickyFirstWhitelisted }
        })
        def firstBuilder = new StringBuilder()
        def firstContext = new StringBuilderWithContext(firstBuilder)
        firstContext.setMaxMessageLength(64)

        when:
        firstContext.appendObjectTo(new StickyFirstWhitelisted("seed"))

        then:
        firstBuilder.toString() == "StickyFirstWhitelisted{seed}"

        when:
        LogRuntimeTestSupport.resetBinding()
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 64 }
            @Override boolean isWhitelisted(Class<?> type) { return false }
        })
        def secondBuilder = new StringBuilder()
        def secondContext = new StringBuilderWithContext(secondBuilder)
        secondContext.setMaxMessageLength(64)
        secondContext.appendObjectTo(new StickyFirstWhitelisted("after"))

        then:
        secondBuilder.toString() == "StickyFirstWhitelisted{after}"
        !secondBuilder.toString().contains("@")
    }

    def "RENDERER_CACHE resolves early-branch renderer first even when a user subtype of Collection, Map, or CharSequence is whitelisted"() {
        given:
        LogRuntime.initializeBinding(new AbstractLogConfig() {
            @Override int getMaxMessageLength() { return 256 }
            @Override boolean isWhitelisted(Class<?> type) { return true }
        })
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(256)

        when:
        context.appendObjectTo(value)

        then:
        builder.toString() == expectedEarlyBranchOutput
        !builder.toString().contains("should-not-be-used")

        where:
        value                                              || expectedEarlyBranchOutput
        new PrecedenceCollectionUserType(["a", "b"])       || "[a, b]"
        new PrecedenceMapUserType([k: "v"])                || "{k=v}"
        new PrecedenceCharSequenceUserType("hello")        || "hello"
    }

    static class BindingWhitelistedSample {
        private final String value

        BindingWhitelistedSample(String value) {
            this.value = value
        }

        @Override
        String toString() {
            return "BindingWhitelistedSample{" + value + "}"
        }
    }

    static class NonWhitelistedSample {
        private final String value

        NonWhitelistedSample(String value) {
            this.value = value
        }

        @Override
        String toString() {
            return "should-not-be-used:" + value
        }
    }

    static class StickyFirstNonWhitelisted {
        private final String value

        StickyFirstNonWhitelisted(String value) {
            this.value = value
        }

        @Override
        String toString() {
            return "should-not-be-used:" + value
        }
    }

    static class StickyFirstWhitelisted {
        private final String value

        StickyFirstWhitelisted(String value) {
            this.value = value
        }

        @Override
        String toString() {
            return "StickyFirstWhitelisted{" + value + "}"
        }
    }

    static class PrecedenceCollectionUserType extends ArrayList<String> {
        PrecedenceCollectionUserType(List<String> items) {
            super(items)
        }

        @Override
        String toString() {
            return "should-not-be-used:precedence-collection"
        }
    }

    static class PrecedenceMapUserType extends LinkedHashMap<String, String> {
        PrecedenceMapUserType(Map<String, String> entries) {
            super(entries)
        }

        @Override
        String toString() {
            return "should-not-be-used:precedence-map"
        }
    }

    static class PrecedenceCharSequenceUserType implements CharSequence {
        private final String value

        PrecedenceCharSequenceUserType(String value) {
            this.value = value
        }

        @Override
        int length() {
            return value.length()
        }

        @Override
        char charAt(int index) {
            return value.charAt(index)
        }

        @Override
        CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end)
        }

        @Override
        String toString() {
            return "should-not-be-used:precedence-charseq"
        }
    }
}
