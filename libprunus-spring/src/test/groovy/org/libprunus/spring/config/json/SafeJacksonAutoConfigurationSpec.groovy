package org.libprunus.spring.config.json

import org.libprunus.spring.config.json.fixture.Dog
import org.libprunus.spring.config.json.fixture.PolymorphicFixtures
import org.libprunus.spring.config.json.fixture.SafeBaseHolder
import org.libprunus.spring.config.json.fixture.UnsafeBaseHolder
import spock.lang.Specification
import tools.jackson.databind.DatabindException
import tools.jackson.databind.json.JsonMapper

class SafeJacksonAutoConfigurationSpec extends Specification {

    private final SafeJacksonAutoConfiguration autoConfiguration = new SafeJacksonAutoConfiguration()

    def "stock Jackson permits safe-base polymorphic deserialization, so any denial below is attributable to this config"() {
        when:
        def holder = JsonMapper.builder().build().readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)

        then:
        holder.payload instanceof Dog
    }

    def "empty allow-list denies safe-base class-name polymorphic deserialization"() {
        given:
        def mapper = mapperAllowing([])
        SafeBaseHolder result = null

        when:
        result = mapper.readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)

        then:
        thrown(DatabindException)
        result == null
    }

    def "allow-listed package permits safe-base polymorphic deserialization, with or without a trailing dot"() {
        given:
        def mapper = mapperAllowing([allowedPackage])

        when:
        def holder = mapper.readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)

        then:
        holder.payload instanceof Dog
        ((Dog) holder.payload).name == "Rex"

        where:
        allowedPackage << [PolymorphicFixtures.FIXTURE_PACKAGE, PolymorphicFixtures.FIXTURE_PACKAGE + "."]
    }

    def "allow-list entry matches on the package boundary, not as a bare character prefix"() {
        given: "an entry that is a character-prefix of, but not, the fixture package"
        def mapper = mapperAllowing([PolymorphicFixtures.FIXTURE_PACKAGE[0..-2]])
        SafeBaseHolder result = null

        when:
        result = mapper.readValue(PolymorphicFixtures.safeBaseJson("Rex"), SafeBaseHolder)

        then:
        thrown(DatabindException)
        result == null
    }

    def "unsafe Object base type stays denied even when its package is allow-listed (base-type limiting preserved)"() {
        given:
        def mapper = mapperAllowing([PolymorphicFixtures.FIXTURE_PACKAGE])
        UnsafeBaseHolder result = null

        when:
        result = mapper.readValue(PolymorphicFixtures.unsafeBaseJson("Rex"), UnsafeBaseHolder)

        then:
        thrown(DatabindException)
        result == null
    }

    private JsonMapper mapperAllowing(List<String> allowedPackages) {
        def customizer = autoConfiguration.prunusPolymorphicTypeValidatorCustomizer(new SafeJacksonProperties(allowedPackages))
        def builder = JsonMapper.builder()
        customizer.customize(builder)
        return builder.build()
    }
}
