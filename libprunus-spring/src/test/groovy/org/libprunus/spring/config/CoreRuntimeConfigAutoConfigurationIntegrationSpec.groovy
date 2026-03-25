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
        given: "the auto-configuration context runner"
        def propertiesCount = 0
        def runtimeConfigCount = 0
        def repositoryCount = 0
        CoreRuntimeConfig runtimeConfig
        ConfigurationRepository repository

        when: "the context starts with only the auto-configuration"
        contextRunner.run { context ->
            propertiesCount = context.getBeansOfType(CoreRuntimeProperties).size()
            runtimeConfigCount = context.getBeansOfType(CoreRuntimeConfig).size()
            repositoryCount = context.getBeansOfType(ConfigurationRepository).size()
            runtimeConfig = context.getBean(CoreRuntimeConfig)
            repository = context.getBean(ConfigurationRepository)
        }

        then: "default beans are registered and repository references runtime config"
        propertiesCount == 1
        runtimeConfigCount == 1
        repositoryCount == 1
        repository.getGlobalSnapshot().is(runtimeConfig)
    }

    def "respects user-provided runtime config bean"() {
        given: "a user-defined runtime config"
        def customRuntimeConfig = new CoreRuntimeConfig()

        and: "placeholders for observed beans"
        CoreRuntimeConfig runtimeConfigFromContext
        ConfigurationRepository repository

        when: "the context starts with a runtime config override"
        contextRunner
                .withBean(CoreRuntimeConfig) { customRuntimeConfig }
                .run { context ->
                    runtimeConfigFromContext = context.getBean(CoreRuntimeConfig)
                    repository = context.getBean(ConfigurationRepository)
                }

        then: "the user runtime config is honored throughout repository wiring"
        runtimeConfigFromContext.is(customRuntimeConfig)
        repository.getGlobalSnapshot().is(customRuntimeConfig)
    }

    def "respects user-provided configuration repository bean"() {
        given: "a user-defined repository"
        def customRepository = new ConfigurationRepository(new CoreRuntimeConfig())

        and: "a placeholder for the resolved repository"
        ConfigurationRepository repositoryFromContext

        when: "the context starts with a repository override"
        contextRunner
                .withBean(ConfigurationRepository) { customRepository }
                .run { context ->
                    repositoryFromContext = context.getBean(ConfigurationRepository)
                }

        then: "the user repository bean is used"
        repositoryFromContext.is(customRepository)
    }

    def "respects user overrides for both runtime config and repository"() {
        given: "user-defined runtime config and repository"
        def customRuntimeConfig = new CoreRuntimeConfig()
        def customRepository = new ConfigurationRepository(customRuntimeConfig)

        and: "placeholders for both resolved beans"
        CoreRuntimeConfig runtimeConfigFromContext
        ConfigurationRepository repositoryFromContext

        when: "the context starts with both overrides"
        contextRunner
                .withBean(CoreRuntimeConfig) { customRuntimeConfig }
                .withBean(ConfigurationRepository) { customRepository }
                .run { context ->
                    runtimeConfigFromContext = context.getBean(CoreRuntimeConfig)
                    repositoryFromContext = context.getBean(ConfigurationRepository)
                }

        then: "both user overrides are retained"
        runtimeConfigFromContext.is(customRuntimeConfig)
        repositoryFromContext.is(customRepository)
    }
}
