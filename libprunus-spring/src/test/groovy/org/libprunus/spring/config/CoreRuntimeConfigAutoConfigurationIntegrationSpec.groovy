package org.libprunus.spring.config

import org.libprunus.core.config.ConfigurationRepository
import org.libprunus.core.config.CoreRuntimeConfig
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

class CoreRuntimeConfigAutoConfigurationIntegrationSpec extends Specification {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CoreRuntimeConfigAutoConfiguration))

    def "registers default beans when user does not provide overrides"() {
        when: "the context starts with only the auto-configuration"
        contextRunner.run { context ->
            assert context.getBeansOfType(CoreRuntimeProperties).size() == 1
            assert context.getBeansOfType(CoreRuntimeConfig).size() == 1
            assert context.getBeansOfType(ConfigurationRepository).size() == 1
            def runtimeConfig = context.getBean(CoreRuntimeConfig)
            def repository = context.getBean(ConfigurationRepository)
            assert repository.getGlobalSnapshot().is(runtimeConfig)
        }

        then: "the context assertions pass"
        noExceptionThrown()
    }

    def "respects user-provided runtime config bean"() {
        given: "a user-defined runtime config"
        def customRuntimeConfig = new CoreRuntimeConfig()

        when: "the context starts with a runtime config override"
        contextRunner
                .withBean(CoreRuntimeConfig) { customRuntimeConfig }
                .run { context ->
                    assert context.getBean(CoreRuntimeConfig).is(customRuntimeConfig)
                    def repository = context.getBean(ConfigurationRepository)
                    assert repository.getGlobalSnapshot().is(customRuntimeConfig)
                }

        then: "the context assertions pass"
        noExceptionThrown()
    }

    def "respects user-provided configuration repository bean"() {
        given: "a user-defined repository"
        def customRepository = new ConfigurationRepository(new CoreRuntimeConfig())

        when: "the context starts with a repository override"
        contextRunner
                .withBean(ConfigurationRepository) { customRepository }
                .run { context ->
                    assert context.getBean(ConfigurationRepository).is(customRepository)
                }

        then: "the context assertions pass"
        noExceptionThrown()
    }

    def "respects user overrides for both runtime config and repository"() {
        given: "user-defined runtime config and repository"
        def customRuntimeConfig = new CoreRuntimeConfig()
        def customRepository = new ConfigurationRepository(customRuntimeConfig)

        when: "the context starts with both overrides"
        contextRunner
                .withBean(CoreRuntimeConfig) { customRuntimeConfig }
                .withBean(ConfigurationRepository) { customRepository }
                .run { context ->
                    assert context.getBean(CoreRuntimeConfig).is(customRuntimeConfig)
                    assert context.getBean(ConfigurationRepository).is(customRepository)
                }

        then: "the context assertions pass"
        noExceptionThrown()
    }
}
