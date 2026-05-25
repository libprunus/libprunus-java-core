package org.libprunus.core.plugin.aot.log

import net.bytebuddy.dynamic.ClassFileLocator
import spock.lang.Specification

class WhitelistClassNameValidatorSpec extends Specification {

    def "validate accepts empty whitelist set without invoking classFileLocator"() {
        given:
        ClassFileLocator locator = Mock()

        when:
        WhitelistClassNameValidator.validate([] as Set, locator)

        then:
        noExceptionThrown()
        0 * locator.locate(_)
    }

    def "validate accepts core builtin whitelist class names without touching classFileLocator"() {
        given:
        ClassFileLocator locator = Mock()

        when:
        WhitelistClassNameValidator.validate(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST.toSet(), locator)

        then:
        noExceptionThrown()
        0 * locator.locate(_)
    }

    def "validate accepts each core builtin whitelist class name individually without invoking classFileLocator"() {
        given:
        ClassFileLocator locator = Mock()

        when:
        WhitelistClassNameValidator.validate([name] as Set, locator)

        then:
        noExceptionThrown()
        0 * locator.locate(_)

        where:
        name << RuntimeBindingAbi.CORE_BUILTIN_WHITELIST
    }

    def "validate rejects empty class name with 'Invalid whitelist class name' diagnostic"() {
        given:
        ClassFileLocator locator = Mock()

        when:
        WhitelistClassNameValidator.validate([""] as Set, locator)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Invalid whitelist class name: "
        ex.cause == null
        0 * locator.locate(_)
    }

    def "validate rejects class names containing array bracket with 'Invalid whitelist class name' diagnostic"() {
        given:
        ClassFileLocator locator = Mock()

        when:
        WhitelistClassNameValidator.validate([name] as Set, locator)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Invalid whitelist class name: " + name
        ex.cause == null
        0 * locator.locate(_)

        where:
        name << ["java.lang.String[]", "[Ljava.lang.String;", "[I", "foo[bar"]
    }

    def "validate rejects primitive type names with 'Invalid whitelist class name' diagnostic"() {
        given:
        ClassFileLocator locator = Mock()

        when:
        WhitelistClassNameValidator.validate([name] as Set, locator)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Invalid whitelist class name: " + name
        ex.cause == null
        0 * locator.locate(_)

        where:
        name << ["boolean", "byte", "char", "short", "int", "long", "float", "double", "void"]
    }

    def "validate short-circuits on first invalid class name without advancing to subsequent entries"() {
        given:
        ClassFileLocator locator = Mock()
        Set<String> names = new LinkedHashSet<>(["int", "java.lang.String"])

        when:
        WhitelistClassNameValidator.validate(names, locator)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Invalid whitelist class name: int"
        ex.cause == null
        0 * locator.locate(_)
    }

    def "validate throws 'Whitelist class cannot be resolved' when classFileLocator returns unresolved result"() {
        given:
        ClassFileLocator locator = Mock()
        def unresolved = new ClassFileLocator.Resolution.Illegal("foo.Bar")
        locator.locate("foo.Bar") >> unresolved

        when:
        WhitelistClassNameValidator.validate(["foo.Bar"] as Set, locator)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Whitelist class cannot be resolved: foo.Bar"
        ex.cause == null
    }

    def "validate wraps classFileLocator IOException with 'Failed to resolve whitelist class' diagnostic"() {
        given:
        ClassFileLocator locator = Mock()
        def ioFailure = new IOException("disk failure")
        locator.locate("foo.Bar") >> { throw ioFailure }

        when:
        WhitelistClassNameValidator.validate(["foo.Bar"] as Set, locator)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Failed to resolve whitelist class: foo.Bar"
        ex.cause.is(ioFailure)
    }

    def "isPrimitiveName returns true for all 9 Java primitive keywords and false otherwise"() {
        expect:
        WhitelistClassNameValidator.isPrimitiveName(name) == expected

        where:
        name      || expected
        "boolean" || true
        "byte"    || true
        "char"    || true
        "short"   || true
        "int"     || true
        "long"    || true
        "float"   || true
        "double"  || true
        "void"    || true
        "Boolean" || false
        "foo"     || false
        ""        || false
    }
}
