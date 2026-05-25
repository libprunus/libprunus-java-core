package org.libprunus.core.plugin.aot.log

import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class MethodLoggingRuleSpec extends Specification {

    def "constructor preserves routeId, levels, and the field-extractor element supplied by caller"() {
        given:
        def extractor = new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/String;", false)
        def rule = new MethodLoggingRule(
                "route-1",
                ["com.app"],
                [],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [extractor])

        expect:
        rule.routeId() == "route-1"
        rule.entryLevel() == LogLevel.DEBUG
        rule.exitLevel() == LogLevel.INFO
        rule.fieldExtractors().size() == 1
        rule.fieldExtractors()[0] == extractor
    }

    def "constructor takes a defensive snapshot of fieldExtractors so subsequent caller mutation does not leak in"() {
        given:
        def original = new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/String;", false)
        def mutable = [original] as ArrayList<FieldExtractorRef>
        def rule = new MethodLoggingRule(
                "route-snapshot",
                ["com.app"],
                [],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                mutable)

        when:
        mutable.add(new FieldExtractorRef("spanId", "sample/Registry", "span", "()Ljava/lang/String;", false))
        mutable.clear()

        then:
        rule.fieldExtractors().size() == 1
        rule.fieldExtractors()[0] == original

        when:
        rule.fieldExtractors().add(original)

        then:
        thrown(UnsupportedOperationException)
        rule.fieldExtractors().size() == 1
    }

    def "constructor takes a defensive snapshot of include, exclude, and suffix lists observable via matches"() {
        given:
        def include = ["com.foo"] as ArrayList<String>
        def exclude = ["com.bar"] as ArrayList<String>
        def suffix = ["Service"] as ArrayList<String>
        def rule = new MethodLoggingRule(
                "route-copy",
                include,
                exclude,
                suffix,
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

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

    def "entryLevel and exitLevel are addressable independently and not aliased"() {
        given:
        def rule = new MethodLoggingRule(
                "route-levels",
                ["com.app"],
                [],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

        expect:
        rule.entryLevel() == LogLevel.DEBUG
        rule.exitLevel() == LogLevel.INFO
        rule.entryLevel() != rule.exitLevel()
    }

    def "fieldExtractors preserves caller-supplied order"() {
        given:
        def fieldA = new FieldExtractorRef("fieldA", "sample/Registry", "a", "()Ljava/lang/String;", false)
        def fieldB = new FieldExtractorRef("fieldB", "sample/Registry", "b", "()Ljava/lang/String;", false)
        def fieldC = new FieldExtractorRef("fieldC", "sample/Registry", "c", "()Ljava/lang/String;", false)
        def rule = new MethodLoggingRule(
                "route-order",
                ["com.app"],
                [],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [fieldA, fieldB, fieldC])

        expect:
        def extractors = rule.fieldExtractors()
        extractors.size() == 3
        extractors[0].fieldName() == "fieldA"
        extractors[1].fieldName() == "fieldB"
        extractors[2].fieldName() == "fieldC"
    }

    def "matches returns true when include prefix hits, no exclude prefix hits, and suffix hits; suffix miss flips it to false"() {
        given:
        def rule = new MethodLoggingRule(
                "route-positive",
                ["com.app"],
                ["com.other"],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

        expect:
        rule.matches("com.app", "OrderService")
        !rule.matches("com.app", "OrderRepository")
    }

    def "matches short-circuits to false when no include prefix hits even if suffix would match"() {
        given:
        def rule = new MethodLoggingRule(
                "route-include-miss",
                ["com.app"],
                [],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

        expect:
        !rule.matches("org.unrelated", "OrderService")
        rule.matches("com.app", "OrderService")
    }

    def "matches short-circuits to false when an exclude prefix hits even though include and suffix would both match, and passes through when exclude misses"() {
        given:
        def rule = new MethodLoggingRule(
                "route-exclude",
                ["com.app"],
                ["com.app.internal"],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

        expect:
        !rule.matches("com.app.internal", "OrderService")
        rule.matches("com.app.public", "OrderService")
    }

    def "matches returns false when include and exclude pass but no suffix matches, contrasted with the suffix-hit path"() {
        given:
        def rule = new MethodLoggingRule(
                "route-suffix-gate",
                ["com.app"],
                [],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

        expect:
        !rule.matches("com.app", "OrderRepository")
        rule.matches("com.app", "OrderService")
    }

    def "matches returns false when include list is empty regardless of suffix hit"() {
        given:
        def rule = new MethodLoggingRule(
                "route-empty-include",
                [],
                [],
                ["Service"],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

        expect:
        !rule.matches("com.app", "OrderService")
    }

    def "matches returns false when suffix list is empty even if include matches"() {
        given:
        def rule = new MethodLoggingRule(
                "route-empty-suffix",
                ["com.app"],
                [],
                [],
                LogLevel.DEBUG,
                LogLevel.INFO,
                [])

        expect:
        !rule.matches("com.app", "OrderService")
    }
}
