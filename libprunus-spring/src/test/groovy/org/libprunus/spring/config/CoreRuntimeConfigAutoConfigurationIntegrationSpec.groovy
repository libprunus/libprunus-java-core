package org.libprunus.spring.config

import java.util.concurrent.atomic.AtomicReference
import org.libprunus.core.config.ConfigurationRepository
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.LogRuntimeConfig
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

class CoreRuntimeConfigAutoConfigurationIntegrationSpec extends Specification {

    private static final CoreRuntimeConfig DEFAULT_LOG_ENABLED_CONFIG =
            new CoreRuntimeConfig(new LogRuntimeConfig(true))

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CoreRuntimeConfigAutoConfiguration))

    def cleanup() {
        // Tests in this spec construct ConfigurationRepository, which mutates LogRuntime's
        // global ACTIVE_CONFIG_REF. Restore a fresh enabled-by-default reference so
        // unrelated specs reading LogRuntime.isEnabled() remain isolated.
        LogRuntime.linkToDataPlane(new AtomicReference<CoreRuntimeConfig>(DEFAULT_LOG_ENABLED_CONFIG))
    }

    def "registers default beans when user does not provide overrides"() {
        given: "placeholders for observed beans"
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

    def "binds libprunus.log.enabled application property end-to-end"() {
        given: "a placeholder for the materialized runtime config"
        CoreRuntimeConfig runtimeConfig

        when: "the context starts with libprunus.log.enabled=false"
        contextRunner
                .withPropertyValues("libprunus.log.enabled=false")
                .run { context ->
                    runtimeConfig = context.getBean(CoreRuntimeConfig)
                }

        then: "the property propagates through CoreRuntimeProperties.toConfig()"
        !runtimeConfig.log().enabled()
    }

    def "respects user-provided runtime config bean"() {
        given: "a user-defined runtime config"
        def customRuntimeConfig =
            new CoreRuntimeConfig(new LogRuntimeConfig(true))

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
        def customRepository = new ConfigurationRepository(
            new CoreRuntimeConfig(new LogRuntimeConfig(true)))

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
        def customRuntimeConfig =
            new CoreRuntimeConfig(new LogRuntimeConfig(true))
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

    def "repository refresh keeps runtime gate aligned in auto-configured context"() {
        given: "an auto-configured repository bean"
        ConfigurationRepository repository

        when: "the context starts and repository is refreshed to disabled"
        contextRunner.run { context ->
            repository = context.getBean(ConfigurationRepository)
            repository.refresh(new CoreRuntimeConfig(new LogRuntimeConfig(false)))
        }

        then: "runtime gate and repository snapshot expose the same enabled flag"
        !repository.getGlobalSnapshot().log().enabled()
        !LogRuntime.isEnabled()
    }
}
