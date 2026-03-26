package org.libprunus.spring.config

import org.springframework.boot.context.properties.ConfigurationProperties
import spock.lang.Specification

class CoreRuntimePropertiesSpec extends Specification {

    def "can instantiate core runtime properties"() {
        when: "a new properties object is created"
        def properties = new CoreRuntimeProperties()

        then: "the object is available"
        properties != null
    }

    def "declares libprunus configuration prefix"() {
        given: "the core runtime properties type"
        def type = CoreRuntimeProperties

        when: "configuration properties metadata is read"
        def annotation = type.getAnnotation(ConfigurationProperties)

        then: "the expected prefix is declared"
        annotation != null
        annotation.prefix() == "libprunus"
    }
}
