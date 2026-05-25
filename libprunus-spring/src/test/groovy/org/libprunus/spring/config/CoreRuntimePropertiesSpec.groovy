package org.libprunus.spring.config

import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import spock.lang.Specification

class CoreRuntimePropertiesSpec extends Specification {

    def "exposes enabled-by-default log config"() {
        given: "a new properties object"
        def properties = new CoreRuntimeProperties()

        expect: "the log section defaults to enabled"
        properties.log != null
        properties.log.enabled()
    }

    def "materializes a CoreRuntimeConfig that mirrors the configured LogRuntimeConfig"() {
        given: "properties with an explicit log config"
        def properties = new CoreRuntimeProperties()
        properties.log = new LogRuntimeConfig(false)

        when: "runtime config is materialized"
        CoreRuntimeConfig runtimeConfig = properties.toConfig()

        then: "the same LogRuntimeConfig instance is carried through"
        runtimeConfig.log().is(properties.log)
        !runtimeConfig.log().enabled()
    }

    def "supports log replacement through the setter"() {
        given: "a properties object"
        def properties = new CoreRuntimeProperties()
        def replacement = new LogRuntimeConfig(false)

        when: "the log setter is invoked"
        properties.setLog(replacement)

        then: "the getter returns the replacement"
        properties.getLog().is(replacement)
    }

    def "binds libprunus.log.enabled from a property source via constructor binding"() {
        given: "a property source carrying the nested key"
        def source = new MapConfigurationPropertySource(["libprunus.log.enabled": value])
        def binder = new Binder([source] as ConfigurationPropertySource[])

        when: "the binder materializes CoreRuntimeProperties"
        def properties = binder.bindOrCreate("libprunus", CoreRuntimeProperties)

        then: "the nested LogRuntimeConfig record is rebuilt with the bound value"
        properties.log.enabled() == expected

        where:
        value   || expected
        "false" || false
        "true"  || true
    }

    def "leaves the default log alone when no libprunus.log.* keys are present"() {
        given: "a property source carrying unrelated keys only"
        def source = new MapConfigurationPropertySource(["other.thing": "x"])
        def binder = new Binder([source] as ConfigurationPropertySource[])

        when: "the binder materializes CoreRuntimeProperties"
        def properties = binder.bindOrCreate("libprunus", CoreRuntimeProperties)

        then: "the constructor default (enabled=true) is preserved"
        properties.log.enabled()
    }
}
