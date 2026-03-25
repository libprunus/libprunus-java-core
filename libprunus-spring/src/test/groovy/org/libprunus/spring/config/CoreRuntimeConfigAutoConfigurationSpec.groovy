package org.libprunus.spring.config

import org.libprunus.core.config.ConfigurationRepository
import org.libprunus.core.config.CoreRuntimeConfig
import spock.lang.Specification

class CoreRuntimeConfigAutoConfigurationSpec extends Specification {

    def "creates runtime config instances from properties"() {
        given: "an auto-configuration instance and properties"
        def autoConfiguration = new CoreRuntimeConfigAutoConfiguration()
        def properties = new CoreRuntimeProperties()

        when: "runtime config is requested twice"
        def first = autoConfiguration.coreRuntimeConfig(properties)
        def second = autoConfiguration.coreRuntimeConfig(properties)

        then: "each request returns a runtime config instance"
        first instanceof CoreRuntimeConfig
        second instanceof CoreRuntimeConfig
    }

    def "creates a repository with the provided runtime config snapshot"() {
        given: "an auto-configuration instance and runtime config"
        def autoConfiguration = new CoreRuntimeConfigAutoConfiguration()
        def runtimeConfig = new CoreRuntimeConfig()

        when: "a repository is created"
        ConfigurationRepository repository = autoConfiguration.configurationRepository(runtimeConfig)

        then: "the repository snapshot matches the provided runtime config"
        repository.getGlobalSnapshot().is(runtimeConfig)
    }

    def "runtime config creation supports normal and null properties inputs"() {
        given: "an auto-configuration instance"
        def autoConfiguration = new CoreRuntimeConfigAutoConfiguration()

        when: "runtime config is requested with varied properties inputs"
        def runtimeConfig = autoConfiguration.coreRuntimeConfig(propertiesInput)

        then: "a runtime config instance is always created"
        runtimeConfig instanceof CoreRuntimeConfig

        where:
        propertiesInput << [new CoreRuntimeProperties(), null]
    }
}
