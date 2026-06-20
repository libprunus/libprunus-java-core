package org.libprunus.spring.config.json

import org.libprunus.spring.config.json.fixture.Dog
import org.libprunus.spring.config.json.fixture.PolymorphicFixtures
import org.libprunus.spring.config.json.fixture.SafeBaseHolder
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification
import tools.jackson.databind.DatabindException
import tools.jackson.databind.json.JsonMapper

class SafeJacksonAutoConfigurationIntegrationSpec extends Specification {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SafeJacksonAutoConfiguration, JacksonAutoConfiguration))

    def "without the autoconfig the JsonMapper permits safe-base polymorphic deserialization (baseline)"() {
        given:
        SafeBaseHolder result = null

        when:
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration))
                .run { context ->
                    result = context.getBean(JsonMapper).readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)
                }

        then:
        result.payload instanceof Dog
    }

    def "with the autoconfig the JsonMapper denies safe-base polymorphic deserialization by default"() {
        given:
        DatabindException failure = null
        SafeBaseHolder result = null

        when:
        contextRunner.run { context ->
            try {
                result = context.getBean(JsonMapper).readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)
            } catch (DatabindException denied) {
                failure = denied
            }
        }

        then:
        failure != null
        result == null
    }

    def "allow-listed package permits safe-base polymorphic deserialization end-to-end"() {
        given:
        SafeBaseHolder result = null

        when:
        contextRunner
                .withPropertyValues("prunus.json.allowed-packages=" + PolymorphicFixtures.FIXTURE_PACKAGE)
                .run { context ->
                    result = context.getBean(JsonMapper).readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)
                }

        then:
        result.payload instanceof Dog
        ((Dog) result.payload).name == "Rex"
    }

    def "a user customizer under the prunus bean name replaces and disables the default gate"() {
        given:
        def userCustomizer = { JsonMapper.Builder builder -> } as JsonMapperBuilderCustomizer
        JsonMapperBuilderCustomizer named = null
        SafeBaseHolder result = null

        when:
        contextRunner
                .withBean("prunusPolymorphicTypeValidatorCustomizer", JsonMapperBuilderCustomizer, { userCustomizer })
                .run { context ->
                    named = context.getBean("prunusPolymorphicTypeValidatorCustomizer", JsonMapperBuilderCustomizer)
                    result = context.getBean(JsonMapper).readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)
                }

        then: "the user bean replaced ours"
        named.is(userCustomizer)

        and: "so the deny-by-default gate is no longer applied"
        result.payload instanceof Dog
    }
}
