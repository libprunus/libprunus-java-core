package org.libprunus.core.plugin.aot

import spock.lang.Specification

class BindingIdGeneratorSpec extends Specification {

    def "generate is deterministic for same stable input tuple"() {
        given:
        def generator = new BindingIdGenerator()

        when:
        def first = generator.generate("org.demo", "app", "1.0.0", ":service", "main")
        def second = generator.generate("org.demo", "app", "1.0.0", ":service", "main")

        then:
        first == second
    }

    def "generate produces a different hash when any one coordinate field changes"() {
        given:
        def generator = new BindingIdGenerator()
        def baseline = generator.generate("org.demo", "app", "1.0.0", ":service", "main")

        expect:
        generator.generate(group, artifact, version, modulePath, variant) != baseline

        where:
        group        | artifact | version | modulePath | variant
        "org.other"  | "app"    | "1.0.0" | ":service" | "main"
        "org.demo"   | "lib"    | "1.0.0" | ":service" | "main"
        "org.demo"   | "app"    | "2.0.0" | ":service" | "main"
        "org.demo"   | "app"    | "1.0.0" | ":other"   | "main"
        "org.demo"   | "app"    | "1.0.0" | ":service" | "test"
    }

    def "generate rejects null or blank modulePath and variant with project-named exception"() {
        given:
        def generator = new BindingIdGenerator()

        when:
        generator.generate("g", "a", "v", modulePath, variant)

        then:
        def ex = thrown(expectedException)
        ex.message == expectedMessage

        where:
        modulePath | variant  || expectedException        | expectedMessage
        null       | "main"   || NullPointerException     | "modulePath"
        ":m"       | null     || NullPointerException     | "variant"
        ""         | "main"   || IllegalArgumentException | "modulePath must not be blank"
        " "        | "main"   || IllegalArgumentException | "modulePath must not be blank"
        "　"   | "main"   || IllegalArgumentException | "modulePath must not be blank"
        ":m"       | ""       || IllegalArgumentException | "variant must not be blank"
        ":m"       | " "      || IllegalArgumentException | "variant must not be blank"
        ":m"       | "　" || IllegalArgumentException | "variant must not be blank"
    }
}
