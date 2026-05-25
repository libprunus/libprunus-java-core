package org.libprunus.core.config

import java.util.concurrent.atomic.AtomicReference
import org.libprunus.core.log.runtime.LogRuntimeConfig
import spock.lang.Specification

class CoreRuntimeConfigSpec extends Specification {

    def "Should reject null LogRuntimeConfig at the compact constructor without producing any side effect."() {
        given:
        def producedRef = new AtomicReference<CoreRuntimeConfig>()

        when:
        producedRef.set(new CoreRuntimeConfig(null))

        then:
        def ex = thrown(NullPointerException)
        ex.message == "log must not be null"
        producedRef.get() == null
    }

    def "Should expose the exact LogRuntimeConfig instance through the generated accessor."() {
        given:
        def validLogConfig = new LogRuntimeConfig(false)

        when:
        def result = new CoreRuntimeConfig(validLogConfig)

        then:
        result.log().is(validLogConfig)
    }

    def "Should not defensively copy the LogRuntimeConfig component on construction."() {
        given:
        def first = new LogRuntimeConfig(true)
        def second = new LogRuntimeConfig(true)

        when:
        def config = new CoreRuntimeConfig(first)

        then:
        config.log().is(first)
        !config.log().is(second)
    }

    def "Should propagate nested LogRuntimeConfig equality so identical enabled flags yield equal CoreRuntimeConfig and divergent flags yield non-equal."() {
        expect:
        (left == right) == expectedEqual

        and: "equals/hashCode contract: equal pairs must hash identically"
        !expectedEqual || left.hashCode() == right.hashCode()

        where:
        left                                                | right                                               || expectedEqual
        new CoreRuntimeConfig(new LogRuntimeConfig(false))  | new CoreRuntimeConfig(new LogRuntimeConfig(false))  || true
        new CoreRuntimeConfig(new LogRuntimeConfig(true))   | new CoreRuntimeConfig(new LogRuntimeConfig(false))  || false
    }

}
