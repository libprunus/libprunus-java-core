package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class ClassPlanAssemblerSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(ClassPlanFixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static MethodNode methodNode(MethodDescription method, List<Family> parameterFamilies, Family returnFamily) {
        new MethodNode(
                method.getInternalName(),
                method.getDescriptor(),
                false,
                true,
                Family.NONE,
                Family.NONE,
                parameterFamilies,
                returnFamily,
                false)
    }

    def "assembleFromRouteGraph translates parameterFamilies and returnFamily into ignored and masked bitmasks"() {
        given:
        def method = fixtureMethod(methodName)
        def node = methodNode(method, parameterFamilies, returnFamily)

        when:
        def plan = ClassPlanAssembler.assembleFromRouteGraph(method, node, LogLevel.DEBUG, LogLevel.WARN)

        then:
        plan.ignoredParamMask() as List == expectedIgnored as List
        plan.maskedParamMask() as List == expectedMasked as List
        plan.returnIgnored() == expectedReturnIgnored
        plan.returnMasked() == expectedReturnMasked

        where:
        methodName  | parameterFamilies                              | returnFamily        || expectedIgnored | expectedMasked | expectedReturnIgnored | expectedReturnMasked
        "twoArgs"   | [Family.NONE, Family.NONE]                     | Family.NONE         || [0L]            | [0L]           | false                 | false
        "twoArgs"   | [Family.SUPPRESS, Family.NONE]                 | Family.NONE         || [1L]            | [0L]           | false                 | false
        "twoArgs"   | [Family.NONE, Family.MASK]                     | Family.NONE         || [0L]            | [2L]           | false                 | false
        "twoArgs"   | [Family.SUPPRESS, Family.MASK]                 | Family.NONE         || [1L]            | [2L]           | false                 | false
        "twoArgs"   | [Family.PASS_THROUGH, Family.NONE]             | Family.NONE         || [0L]            | [0L]           | false                 | false
        "noArgs"    | []                                             | Family.SUPPRESS     || []              | []             | true                  | false
        "noArgs"    | []                                             | Family.MASK         || []              | []             | false                 | true
        "noArgs"    | []                                             | Family.PASS_THROUGH || []              | []             | false                 | false
        "noArgs"    | []                                             | Family.NONE         || []              | []             | false                 | false
    }

    def "assembleFromRouteGraph projects MethodKey from MethodDescription and passes through both LogLevels"() {
        given:
        def method = fixtureMethod("oneArg")
        def node = methodNode(method, [Family.NONE], Family.NONE)

        when:
        def plan = ClassPlanAssembler.assembleFromRouteGraph(method, node, LogLevel.DEBUG, LogLevel.WARN)

        then:
        plan.methodKey().ownerInternalName() == FIXTURE_TYPE.getInternalName()
        plan.methodKey().methodName() == "oneArg"
        plan.methodKey().methodDescriptor() == "(Ljava/lang/String;)Ljava/lang/String;"
        plan.effectiveEnterLevel() == LogLevel.DEBUG
        plan.effectiveExitLevel() == LogLevel.WARN
        plan.effectiveEnterLevel() != plan.effectiveExitLevel()
    }

    def "MethodPlan compact constructor round-trips methodKey identity, mask contents, return flags, and both LogLevels"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "findById", "(Ljava/lang/String;)Ljava/lang/String;")
        def ignoredMask = [5L] as long[]
        def maskedMask = [6L] as long[]

        when:
        def plan = new ClassPlanAssembler.MethodPlan(
                methodKey, ignoredMask, maskedMask, true, false, LogLevel.INFO, LogLevel.ERROR)

        then:
        plan.methodKey().is(methodKey)
        plan.ignoredParamMask() as List == ignoredMask as List
        plan.maskedParamMask() as List == maskedMask as List
        plan.returnMasked()
        !plan.returnIgnored()
        plan.effectiveEnterLevel() == LogLevel.INFO
        plan.effectiveExitLevel() == LogLevel.ERROR
        plan.effectiveEnterLevel() != plan.effectiveExitLevel()
    }

    def "MethodPlan compact constructor preserves null masks and short-circuits isParam queries"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "secret", "()Ljava/lang/String;")

        when:
        def plan = new ClassPlanAssembler.MethodPlan(
                methodKey, null, null, false, false, LogLevel.INFO, LogLevel.INFO)

        then:
        plan.ignoredParamMask() == null
        plan.maskedParamMask() == null
        !plan.isParamIgnored(0)
        !plan.isParamMasked(0)
    }

    def "MethodPlan compact constructor stores returnIgnored true and returnMasked false independently"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "secret", "()Ljava/lang/String;")

        when:
        def plan = new ClassPlanAssembler.MethodPlan(
                methodKey, null, null, false, true, LogLevel.INFO, LogLevel.INFO)

        then:
        plan.returnIgnored()
        !plan.returnMasked()
    }

    def "MethodPlan compact constructor clones ignoredParamMask so caller mutation is not observed"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "update", "(J)V")
        def originalMask = [7L] as long[]

        when:
        def plan = new ClassPlanAssembler.MethodPlan(
                methodKey, originalMask, null, false, false, LogLevel.INFO, LogLevel.INFO)
        originalMask[0] = 0L

        then:
        plan.ignoredParamMask()[0] == 7L
        plan.maskedParamMask() == null
        !plan.ignoredParamMask().is(originalMask)
    }

    def "MethodPlan compact constructor clones maskedParamMask so caller mutation is not observed"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "update", "(J)V")
        def originalMask = [3L] as long[]

        when:
        def plan = new ClassPlanAssembler.MethodPlan(
                methodKey, null, originalMask, false, false, LogLevel.INFO, LogLevel.INFO)
        originalMask[0] = 0L

        then:
        plan.maskedParamMask()[0] == 3L
        plan.ignoredParamMask() == null
        !plan.maskedParamMask().is(originalMask)
    }

    def "MethodPlan accessor returns a defensive clone so caller mutation does not leak into the plan"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "fetch", "()V")
        def plan = new ClassPlanAssembler.MethodPlan(
                methodKey, [11L] as long[], [22L] as long[], false, false, LogLevel.INFO, LogLevel.INFO)

        when:
        def firstIgnored = plan.ignoredParamMask()
        def secondIgnored = plan.ignoredParamMask()
        def firstMasked = plan.maskedParamMask()
        def secondMasked = plan.maskedParamMask()
        firstIgnored[0] = 0L
        firstMasked[0] = 0L

        then:
        !firstIgnored.is(secondIgnored)
        !firstMasked.is(secondMasked)
        secondIgnored[0] == 11L
        secondMasked[0] == 22L
        plan.isParamIgnored(0)
        plan.isParamMasked(1)
    }

    def "MethodPlan isParamIgnored returns the configured bit with null and out-of-bounds short-circuit"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "get", "()V")
        def methodPlan = new ClassPlanAssembler.MethodPlan(methodKey, mask, null, false, false, LogLevel.INFO, LogLevel.INFO)

        expect:
        methodPlan.isParamIgnored(index) == expected

        where:
        mask                 | index || expected
        null                 | 0     || false
        [0b10L] as long[]    | 1     || true
        [0L, 0b1L] as long[] | 64    || true
        [0b1L] as long[]     | 128   || false
    }

    def "MethodPlan isParamMasked returns the configured bit with null and out-of-bounds short-circuit"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/ApiService", "get", "()V")
        def methodPlan = new ClassPlanAssembler.MethodPlan(methodKey, null, mask, false, false, LogLevel.INFO, LogLevel.INFO)

        expect:
        methodPlan.isParamMasked(index) == expected

        where:
        mask                 | index || expected
        null                 | 0     || false
        [0b10L] as long[]    | 1     || true
        [0L, 0b1L] as long[] | 64    || true
        [0b1L] as long[]     | 128   || false
    }

    @SuppressWarnings("unused")
    static class ClassPlanFixture {
        void noArgs() {}

        String oneArg(String s) { s }

        void twoArgs(String s, int i) {}
    }
}
