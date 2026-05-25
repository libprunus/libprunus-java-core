package org.libprunus.spring.config

import org.libprunus.core.config.ConfigurationRepository
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.springframework.core.io.DefaultResourceLoader
import spock.lang.Specification

class CoreRuntimeConfigAutoConfigurationSpec extends Specification {

    private final CoreRuntimeConfigAutoConfiguration autoConfiguration =
            new CoreRuntimeConfigAutoConfiguration(new DefaultResourceLoader())

    def "materializes runtime config from properties on every call"() {
        given: "a properties object"
        def properties = new CoreRuntimeProperties()
        properties.log = new LogRuntimeConfig(false)

        when: "runtime config is requested twice"
        def first = autoConfiguration.coreRuntimeConfig(properties)
        def second = autoConfiguration.coreRuntimeConfig(properties)

        then: "each call returns a distinct snapshot carrying the configured value"
        !first.is(second)
        first == second
        !first.log().enabled()
    }

    def "wraps the supplied runtime config in a repository without altering it"() {
        given: "a runtime config"
        def runtimeConfig = new CoreRuntimeConfig(new LogRuntimeConfig(true))

        when: "a repository is created"
        ConfigurationRepository repository = autoConfiguration.configurationRepository(runtimeConfig)

        then: "the repository exposes the same snapshot instance"
        repository.getGlobalSnapshot().is(runtimeConfig)
    }

    def "afterPropertiesSet is a no-op when no AOT callsite resource is present"() {
        when: "the lifecycle callback runs against a classloader without callsite resource"
        autoConfiguration.afterPropertiesSet()

        then: "no exception is thrown"
        noExceptionThrown()
    }
}
