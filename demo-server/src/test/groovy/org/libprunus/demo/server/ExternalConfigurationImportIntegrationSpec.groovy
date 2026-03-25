package org.libprunus.demo.server

import org.libprunus.core.config.ConfigurationRepository
import org.libprunus.core.config.CoreRuntimeConfig
import org.libprunus.spring.config.CoreRuntimeConfigAutoConfiguration
import org.libprunus.spring.config.CoreRuntimeProperties
import org.libprunus.spring.server.management.ManagementConfiguration
import org.libprunus.spring.server.management.ManagementController
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import spock.lang.Specification

class ExternalConfigurationImportIntegrationSpec extends Specification {

    def "demo server context resolves all imported external beans"() {
        given: "a demo-server context importing external spring-server configurations"
        def context = new AnnotationConfigApplicationContext(ImportedConfiguration)

        when: "external beans are resolved from the demo-server context"
        def runtimeProperties = context.getBean(CoreRuntimeProperties)
        def runtimeConfig = context.getBean(CoreRuntimeConfig)
        def configurationRepository = context.getBean(ConfigurationRepository)
        def managementController = context.getBean(ManagementController)

        then: "all imported beans are available"
        runtimeProperties != null
        runtimeConfig != null
        configurationRepository != null
        managementController != null
        configurationRepository.globalSnapshot.is(runtimeConfig)

        cleanup:
        context.close()
    }

    def "imported management controller health endpoint is operational"() {
        given: "a demo-server context importing external spring-server configurations"
        def context = new AnnotationConfigApplicationContext(ImportedConfiguration)

        when: "the imported management health endpoint is invoked"
        def healthResponse = context.getBean(ManagementController).health()

        then: "the response is successful and contains health payload"
        healthResponse.statusCode.is2xxSuccessful()
        healthResponse.body == "health"

        cleanup:
        context.close()
    }

    def "imported configuration remains stable across repeated context bootstrap"() {
        given: "a sequence of independent context bootstrap attempts"
        def healthPayloads = []

        when: "the imported context is started and closed repeatedly"
        (1..bootstrapCount).each {
            def context = new AnnotationConfigApplicationContext(ImportedConfiguration)
            try {
                healthPayloads << context.getBean(ManagementController).health().body
            } finally {
                context.close()
            }
        }

        then: "each bootstrap yields an operational imported health endpoint"
        healthPayloads.size() == bootstrapCount
        healthPayloads.every { it == "health" }

        where:
        bootstrapCount << [1, 2, 3]
    }

    @Configuration
    @Import([ManagementConfiguration, CoreRuntimeConfigAutoConfiguration])
    static class ImportedConfiguration {}
}
