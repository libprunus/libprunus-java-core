package org.libprunus.spring.config.json

import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import spock.lang.Specification

class SafeJacksonPropertiesSpec extends Specification {

    def "defaults to an empty allow-list when no property is present"() {
        given:
        def source = new MapConfigurationPropertySource(["other.thing": "x"])
        def binder = new Binder([source] as ConfigurationPropertySource[])

        when:
        def properties = binder.bindOrCreate("prunus.json", SafeJacksonProperties)

        then:
        properties.allowedPackages().isEmpty()
    }

    def "binds prunus.json.allowed-packages from a property source preserving order"() {
        given:
        def source = new MapConfigurationPropertySource([
                "prunus.json.allowed-packages[0]": "com.example.one",
                "prunus.json.allowed-packages[1]": "com.example.two"])
        def binder = new Binder([source] as ConfigurationPropertySource[])

        when:
        def properties = binder.bindOrCreate("prunus.json", SafeJacksonProperties)

        then:
        properties.allowedPackages() == ["com.example.one", "com.example.two"]
    }

    def "compact constructor copies the list, decoupling from the source and rejecting mutation"() {
        given:
        def source = ["com.example.one"]
        def properties = new SafeJacksonProperties(source)

        when: "the original source list is mutated after construction"
        source.add("com.example.injected")

        then: "the record's list is unaffected by the external mutation"
        properties.allowedPackages() == ["com.example.one"]

        when: "mutation of the record's own list is attempted"
        properties.allowedPackages().add("com.example.injected")

        then: "it is rejected because List.copyOf is unmodifiable"
        thrown(UnsupportedOperationException)
    }
}
