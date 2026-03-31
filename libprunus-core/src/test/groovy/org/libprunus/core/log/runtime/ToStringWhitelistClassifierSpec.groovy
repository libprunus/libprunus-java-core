package org.libprunus.core.log.runtime

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.UUID
import spock.lang.Specification

class ToStringWhitelistClassifierSpec extends Specification {

    def "sanitize filters null class entries from input array"() {
        when: "sanitize is invoked with the given input"
        Class<?>[] result = ToStringWhitelistClassifier.sanitize(input)

        then: "output contains only non-null classes in their original order"
        result as List == expected

        where:
        input                              || expected
        null as Class[]                    || []
        [] as Class[]                      || []
        [null, null] as Class[]            || []
        [String, null, Integer] as Class[] || [String, Integer]
        [String, Integer] as Class[]       || [String, Integer]
    }

    def "resolveToStringWhitelistStatus returns the correct whitelist verdict for each type category"() {
        when: "the whitelist status is resolved for the type"
        boolean result = ToStringWhitelistClassifier.resolveToStringWhitelistStatus(type)

        then: "the verdict matches the expected whitelist classification"
        result == expected

        where:
        type          || expected
        Thread.State  || true
        BigDecimal    || true
        StringBuilder || true
        Instant       || true
        UUID          || true
        Object        || false
    }

    def "resolveToStringWhitelistStatus propagates NullPointerException when type is null"() {
        when: "resolveToStringWhitelistStatus is called with null"
        ToStringWhitelistClassifier.resolveToStringWhitelistStatus(null)

        then: "the underlying isAssignableFrom call throws NullPointerException"
        thrown(NullPointerException)
    }

    def "ToStringWhitelistClassifier private constructor throws UnsupportedOperationException"() {
        when: "the private constructor is invoked directly"
        new ToStringWhitelistClassifier()

        then: "instantiation is rejected with UnsupportedOperationException"
        thrown(UnsupportedOperationException)
    }

    def "WHITELIST_CACHE computeValue delegates whitelist resolution for each type"() {
        given: "direct access to the ClassValue cache instance"
        def cache = ToStringWhitelistClassifier.@WHITELIST_CACHE

        when: "computeValue is invoked directly on the anonymous ClassValue"
        boolean result = cache.computeValue(type)

        then: "the whitelist verdict matches the expected classification"
        result == expected

        where:
        type         || expected
        String       || true
        Thread.State || true
        Object       || false
    }

    def "WHITELIST_CACHE computeValue propagates NullPointerException when type is null"() {
        given: "direct access to the ClassValue cache instance"
        def cache = ToStringWhitelistClassifier.@WHITELIST_CACHE

        when: "computeValue is called with null"
        cache.computeValue(null)

        then: "NullPointerException propagates from the underlying isAssignableFrom call"
        thrown(NullPointerException)
    }

    def "isToStringWhitelisted accepts a type that satisfies a built-in CharSequence contract"() {
        given: "String, which implements CharSequence, is a canonical built-in whitelisted type"
        def targetType = String

        when: "the type is submitted to the whitelist classifier"
        def result = ToStringWhitelistClassifier.isToStringWhitelisted(targetType)

        then: "the classifier confirms the type is whitelisted"
        result == true
    }

    def "isToStringWhitelisted rejects a type with no built-in or custom whitelist entry"() {
        given: "Object has no association to any built-in whitelist family"
        def targetType = Object

        when: "the type is submitted to the whitelist classifier"
        def result = ToStringWhitelistClassifier.isToStringWhitelisted(targetType)

        then: "the classifier rejects the type"
        result == false
    }

    def "isToStringWhitelisted returns a consistent result on the second call via the ClassValue cache"() {
        given: "BigDecimal is evaluated once to populate the cache"
        def targetType = BigDecimal
        ToStringWhitelistClassifier.isToStringWhitelisted(targetType)

        when: "the same type is evaluated again"
        def result = ToStringWhitelistClassifier.isToStringWhitelisted(targetType)

        then: "the cached result is returned without recomputation"
        result == true
    }

    def "isToStringWhitelisted classifies supported families"() {
        expect: "terminal families are recognized"
        ToStringWhitelistClassifier.isToStringWhitelisted(type)

        where:
        type << [
                String,
                StringBuilder,
                Integer,
                Long,
                BigDecimal,
                Boolean,
                Character,
                UUID,
                Date,
                LocalDate,
                Instant,
                SampleState,
                Class
        ]
    }

    def "isToStringWhitelisted rejects non-whitelisted container and custom object types"() {
        expect: "non-terminal categories return false"
        !ToStringWhitelistClassifier.isToStringWhitelisted(type)

        where:
        type << [
                Object,
                Map.class,
                Collection.class,
                int[].class,
                NonTerminalValue
        ]
    }

    def "registry falls back to empty types when generated host proxy is absent"() {
        expect: "no generated proxy class yields an empty custom type array"
        AotToStringWhitelistRegistry.resolveToStringWhitelistTypes().length == 0
    }

    def "isToStringWhitelisted remains stable across repeated lookups"() {
        when: "the same type is queried multiple times"
        def first = ToStringWhitelistClassifier.isToStringWhitelisted(type)
        def second = ToStringWhitelistClassifier.isToStringWhitelisted(type)
        def third = ToStringWhitelistClassifier.isToStringWhitelisted(type)

        then: "result is stable for cached and non-cached calls"
        first == expected
        second == expected
        third == expected

        where:
        type             || expected
        String           || true
        LocalDate        || true
        NonTerminalValue || false
    }

    def "isToStringWhitelisted rejects null type"() {
        when: "null is passed"
        ToStringWhitelistClassifier.isToStringWhitelisted(null)

        then: "a null-pointer exception is raised"
        def exception = thrown(NullPointerException)
        exception.message == "type must not be null"
    }

    private static final class NonTerminalValue {}

    private enum SampleState {
        READY
    }
}
