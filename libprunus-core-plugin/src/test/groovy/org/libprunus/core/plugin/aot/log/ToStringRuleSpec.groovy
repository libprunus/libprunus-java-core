package org.libprunus.core.plugin.aot.log

import spock.lang.Specification

class ToStringRuleSpec extends Specification {

    def "constructor preserves routeId verbatim"() {
        given:
        def rule = new ToStringRule(
                "ts-route-1",
                ["com.app"],
                ["com.app.internal"],
                ["Service"])

        expect:
        rule.routeId() == "ts-route-1"
    }

    def "constructor takes a defensive snapshot of include, exclude, and suffix lists observable via matches"() {
        given:
        def include = ["com.foo"] as ArrayList<String>
        def exclude = ["com.bar"] as ArrayList<String>
        def suffix = ["Service"] as ArrayList<String>
        def rule = new ToStringRule("ts-route-copy", include, exclude, suffix)

        expect:
        rule.matches("com.foo", "OrderService")
        !rule.matches("com.bar", "OrderService")

        when:
        include.clear()
        exclude.clear()
        suffix.clear()

        then:
        rule.matches("com.foo", "OrderService")
        !rule.matches("com.bar", "OrderService")
    }

    def "matches returns true when an include prefix hits, no exclude prefix hits, and a suffix matches"() {
        given:
        def rule = new ToStringRule(
                "ts-route-positive",
                ["com.app"],
                ["com.other"],
                ["Service"])

        expect:
        rule.matches("com.app", "OrderService")
        !rule.matches("com.app", "OrderRepository")
    }

    def "matches short-circuits to false when no include prefix hits even if suffix would match"() {
        given:
        def rule = new ToStringRule(
                "ts-route-include-miss",
                ["com.app"],
                [],
                ["Service"])

        expect:
        !rule.matches("org.unrelated", "OrderService")
        rule.matches("com.app", "OrderService")
    }

    def "matches short-circuits to false when an exclude prefix hits even though include and suffix would both match, and passes through when exclude misses"() {
        given:
        def rule = new ToStringRule(
                "ts-route-exclude",
                ["com.app"],
                ["com.app.internal"],
                ["Service"])

        expect:
        !rule.matches("com.app.internal", "OrderService")
        rule.matches("com.app.public", "OrderService")
    }

    def "matches returns false when include and exclude pass but no suffix matches, contrasted with the suffix-hit path"() {
        given:
        def rule = new ToStringRule(
                "ts-route-suffix-gate",
                ["com.app"],
                [],
                ["Service"])

        expect:
        !rule.matches("com.app", "OrderRepository")
        rule.matches("com.app", "OrderService")
    }

    def "matches returns false when include list is empty regardless of suffix hit"() {
        given:
        def rule = new ToStringRule(
                "ts-route-empty-include",
                [],
                [],
                ["Service"])

        expect:
        !rule.matches("com.app", "OrderService")
    }

    def "matches returns false when suffix list is empty even if include matches"() {
        given:
        def rule = new ToStringRule(
                "ts-route-empty-suffix",
                ["com.app"],
                [],
                [])

        expect:
        !rule.matches("com.app", "OrderService")
    }
}
