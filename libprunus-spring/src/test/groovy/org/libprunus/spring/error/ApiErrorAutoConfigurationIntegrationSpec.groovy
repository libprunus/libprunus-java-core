package org.libprunus.spring.error

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import spock.lang.Specification

class ApiErrorAutoConfigurationIntegrationSpec extends Specification {

    private final WebApplicationContextRunner servletRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ApiErrorAutoConfiguration))

    def "registers the ApiErrorHandler in a servlet web application"() {
        given:
        int count = 0

        when:
        servletRunner.run { context -> count = context.getBeansOfType(ApiErrorHandler).size() }

        then:
        count == 1
    }

    def "backs off when the application already defines an ApiErrorHandler bean"() {
        given:
        def custom = new ApiErrorHandler()
        ApiErrorHandler resolved = null

        when:
        servletRunner.withBean(ApiErrorHandler, { custom }).run { context ->
            resolved = context.getBean(ApiErrorHandler)
        }

        then:
        resolved.is(custom)
    }

    def "does not register the handler outside a servlet web application"() {
        given:
        boolean present = true

        when:
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApiErrorAutoConfiguration))
                .run { context -> present = !context.getBeansOfType(ApiErrorHandler).isEmpty() }

        then:
        !present
    }
}
