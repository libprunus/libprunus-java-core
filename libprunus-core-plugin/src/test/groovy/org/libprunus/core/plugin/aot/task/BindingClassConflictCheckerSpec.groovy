package org.libprunus.core.plugin.aot.task

import spock.lang.Specification

class BindingClassConflictCheckerSpec extends Specification {

    def "checkSpiDescriptorUniqueness returns empty when at most one descriptor source exists"() {
        expect:
        BindingClassConflictChecker.checkSpiDescriptorUniqueness(sources).empty

        where:
        sources       || _
        []            || _
        ["only.jar"]  || _
    }

    def "checkSpiDescriptorUniqueness conflict message lists every offending jar source"() {
        when:
        def result = BindingClassConflictChecker.checkSpiDescriptorUniqueness(["a.jar", "b.jar"])

        then:
        result.present
        def msg = result.get()
        msg.contains("Multiple SPI service descriptors for LogConfig")
        msg.contains("a.jar")
        msg.contains("b.jar")
        !msg.contains("c.jar")
    }

    def "checkSpiDescriptorUniqueness treats duplicate jar entries as a single source"() {
        expect:
        BindingClassConflictChecker.checkSpiDescriptorUniqueness(["a.jar", "a.jar"]).empty
        BindingClassConflictChecker.checkSpiDescriptorUniqueness(["a.jar", "a.jar", "a.jar"]).empty
    }

    def "checkBindingClassUniqueness returns empty when binding class appears in at most one jar"() {
        expect:
        BindingClassConflictChecker.checkBindingClassUniqueness("org.example.Binding", sources).empty

        where:
        sources         || _
        []              || _
        ["single.jar"]  || _
    }

    def "checkBindingClassUniqueness conflict message embeds binding class and every conflicting jar"() {
        when:
        def result = BindingClassConflictChecker.checkBindingClassUniqueness("org.example.Binding", ["x.jar", "y.jar"])

        then:
        result.present
        def msg = result.get()
        msg.contains("org.example.Binding")
        msg.contains("x.jar")
        msg.contains("y.jar")
        msg.contains("found in multiple jars")
        !msg.contains("z.jar")
        !msg.contains("other.Binding")
    }

    def "checkBindingClassUniqueness treats duplicate jar entries as a single source"() {
        expect:
        BindingClassConflictChecker.checkBindingClassUniqueness("org.example.Binding", ["only.jar", "only.jar"]).empty
        BindingClassConflictChecker.checkBindingClassUniqueness("org.example.Binding", ["only.jar", "only.jar", "only.jar"]).empty
    }

    def "checkBindingClassPresent returns empty when binding class is found"() {
        when:
        def result = BindingClassConflictChecker.checkBindingClassPresent("org.example.Binding", true)

        then:
        result.empty
    }

    def "checkBindingClassPresent conflict message embeds the missing binding class name"() {
        when:
        def result = BindingClassConflictChecker.checkBindingClassPresent("org.example.Binding", false)

        then:
        result.present
        def msg = result.get()
        msg.contains("org.example.Binding")
        msg.contains("not found in classpath")
        !msg.contains("found in multiple jars")
        !msg.contains("other.Binding")
    }
}
