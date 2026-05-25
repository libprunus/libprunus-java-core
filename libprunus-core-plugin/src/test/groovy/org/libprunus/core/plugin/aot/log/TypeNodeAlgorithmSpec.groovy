package org.libprunus.core.plugin.aot.log

import java.util.Optional
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class TypeNodeAlgorithmSpec extends Specification {

    private static MethodNode methodNode(String name, String descriptor) {
        new MethodNode(
                name, descriptor,
                false, true, Family.NONE, Family.NONE,
                List.of(), Family.NONE, false)
    }

    private static TypeNode buildWithMethods(List<MethodNode> methodNodes) {
        new TypeNode(
                "sample", "Subject",
                false, false, Family.NONE,
                Optional.empty(), Optional.empty(),
                methodNodes, List.of(), List.of())
    }

    private static MethodLoggingRule sampleMethodRule() {
        new MethodLoggingRule(
                "route", List.of(), List.of(), List.of(),
                LogLevel.DEBUG, LogLevel.DEBUG, List.of())
    }

    private static ToStringRule sampleToStringRule() {
        new ToStringRule("route", List.of(), List.of(), List.of())
    }

    def "constructor defensively copies declaredMethods so later mutations to the source list do not leak into declaredMethods()"() {
        given:
        def original = methodNode("alpha", "()V")
        def mutableInput = new ArrayList<MethodNode>([original])
        def node = buildWithMethods(mutableInput)

        when:
        mutableInput.add(methodNode("beta", "()V"))

        then:
        node.declaredMethods().size() == 1
        node.declaredMethods().first().is(original)
    }

    def "findDeclaredMethod returns the indexed node when name and descriptor match exactly"() {
        given:
        def target = methodNode("alpha", "()V")
        def node = buildWithMethods([target, methodNode("beta", "()V")])

        when:
        def result = node.findDeclaredMethod("alpha", "()V")

        then:
        result.is(target)
    }

    def "findDeclaredMethod returns null when the requested descriptor does not match any indexed entry"() {
        given:
        def node = buildWithMethods([methodNode("alpha", "()V")])

        when:
        def result = node.findDeclaredMethod("alpha", "(I)V")

        then:
        result == null
    }

    def "findDeclaredMethod returns null when the requested name does not match any indexed entry"() {
        given:
        def node = buildWithMethods([methodNode("alpha", "()V")])

        when:
        def result = node.findDeclaredMethod("beta", "()V")

        then:
        result == null
    }

    def "findDeclaredMethod returns the matching node when several entries share a name but only one matches the requested descriptor"() {
        given:
        def voidOverload = methodNode("alpha", "()V")
        def intOverload = methodNode("alpha", "(I)V")
        def node = buildWithMethods([voidOverload, intOverload])

        when:
        def voidResult = node.findDeclaredMethod("alpha", "()V")
        def intResult = node.findDeclaredMethod("alpha", "(I)V")

        then:
        voidResult.is(voidOverload)
        intResult.is(intOverload)
    }

    def "methodEligible projects methodRule.isPresent together with isRegistryClass and hasClassLevelIgnore through methodEligibleOf"() {
        given:
        def node = new TypeNode(
                "sample", "Subject",
                isRegistryClass, hasClassLevelIgnore, Family.NONE,
                methodRulePresent ? Optional.of(sampleMethodRule()) : Optional.<MethodLoggingRule> empty(),
                Optional.<ToStringRule> empty(),
                List.of(), List.of(), List.of())

        expect:
        node.methodEligible() == expected

        where:
        methodRulePresent | isRegistryClass | hasClassLevelIgnore || expected
        false             | false           | false               || false
        false             | false           | true                || false
        false             | true            | false               || false
        false             | true            | true                || false
        true              | false           | false               || true
        true              | false           | true                || false
        true              | true            | false               || false
        true              | true            | true                || false
    }

    def "toStringEligible projects toStringRule.isPresent together with isRegistryClass and hasClassLevelIgnore through toStringEligibleOf"() {
        given:
        def node = new TypeNode(
                "sample", "Subject",
                isRegistryClass, hasClassLevelIgnore, Family.NONE,
                Optional.<MethodLoggingRule> empty(),
                toStringRulePresent ? Optional.of(sampleToStringRule()) : Optional.<ToStringRule> empty(),
                List.of(), List.of(), List.of())

        expect:
        node.toStringEligible() == expected

        where:
        toStringRulePresent | isRegistryClass | hasClassLevelIgnore || expected
        false               | false           | false               || false
        false               | false           | true                || false
        false               | true            | false               || false
        false               | true            | true                || false
        true                | false           | false               || true
        true                | false           | true                || false
        true                | true            | false               || false
        true                | true            | true                || false
    }

    def "methodEligibleOf returns true only when a method rule is present and the type is neither a registry class nor class-level-ignored"() {
        expect:
        TypeNode.methodEligibleOf(methodRulePresent, isRegistryClass, hasClassLevelIgnore) == expected

        where:
        methodRulePresent | isRegistryClass | hasClassLevelIgnore || expected
        false             | false           | false               || false
        false             | false           | true                || false
        false             | true            | false               || false
        false             | true            | true                || false
        true              | false           | false               || true
        true              | false           | true                || false
        true              | true            | false               || false
        true              | true            | true                || false
    }

    def "toStringEligibleOf returns true only when a toString rule is present and the type is neither a registry class nor class-level-ignored"() {
        expect:
        TypeNode.toStringEligibleOf(toStringRulePresent, isRegistryClass, hasClassLevelIgnore) == expected

        where:
        toStringRulePresent | isRegistryClass | hasClassLevelIgnore || expected
        false               | false           | false               || false
        false               | false           | true                || false
        false               | true            | false               || false
        false               | true            | true                || false
        true                | false           | false               || true
        true                | false           | true                || false
        true                | true            | false               || false
        true                | true            | true                || false
    }
}
