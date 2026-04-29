package org.libprunus.core.config

import org.libprunus.core.log.runtime.LogRuntimeConfig
import spock.lang.Specification

class CoreRuntimeConfigSpec extends Specification {

    def "Should successfully initialize CoreRuntimeConfig when provided with a valid LogRuntimeConfig."() {
        given: "a valid non-null runtime log configuration"
        def validLogConfig = new LogRuntimeConfig(false)

        when: "the core runtime config is initialized"
        def result = new CoreRuntimeConfig(validLogConfig)

        then: "the object is created and preserves the provided log configuration"
        result != null
        result.log().is(validLogConfig)
    }

    def "Should reject initialization and throw exception when log config is missing."() {
        when: "the core runtime config is initialized with an invalid input"
        new CoreRuntimeConfig(logInput)

        then: "the expected exception semantics are enforced"
        def ex = thrown(expectedException)
        ex.message == expectedMessage
        0 * _

        where: "invalid input to expected exception mapping"
        logInput || expectedException    | expectedMessage
        null     || NullPointerException | "log must not be null"
    }

}
