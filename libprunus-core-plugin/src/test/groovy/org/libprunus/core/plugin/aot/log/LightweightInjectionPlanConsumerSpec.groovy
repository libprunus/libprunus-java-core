package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class LightweightInjectionPlanConsumerSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    @SuppressWarnings("unused")
    static class Fixture {
        void voidNoArgs() {}
        int intReturn(int x) { return x }
        long wideReturn(double d) { return 0L }
        String referenceReturn() { "" }
        static int staticNoArgs() { 0 }
        void mixedWide(long a, int b, double c, String d) {}
        static void staticWideOnly(long a, double b) {}
    }

    def "consume returns plan whose returnType reflects ASM Type for the method descriptor"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, LogLevel.INFO, LogLevel.INFO, "", false)

        then:
        plan.returnType() == expectedReturnType

        where:
        methodName        || expectedReturnType
        "voidNoArgs"      || Type.VOID_TYPE
        "intReturn"       || Type.INT_TYPE
        "wideReturn"      || Type.LONG_TYPE
        "referenceReturn" || Type.getType(String)
    }

    def "consume returns plan whose firstLocal accounts for this slot and wide parameter sizes"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, LogLevel.OFF, LogLevel.OFF, "", false)

        then:
        plan.firstLocal() == expectedFirstLocal

        where:
        methodName       || expectedFirstLocal
        "voidNoArgs"     || 1
        "intReturn"      || 2
        "wideReturn"     || 3
        "mixedWide"      || 7
        "staticNoArgs"   || 0
        "staticWideOnly" || 4
    }

    def "consume allocates returnValueSlot only when return type is non-void and exit logging is active"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, enter, exit, "", false)

        then:
        plan.returnValueSlot() == expectedReturnValueSlot

        where:
        methodName   | enter         | exit          || expectedReturnValueSlot
        "voidNoArgs" | LogLevel.INFO | LogLevel.INFO || -1
        "voidNoArgs" | LogLevel.OFF  | LogLevel.OFF  || -1
        "intReturn"  | LogLevel.OFF  | LogLevel.OFF  || -1
        "intReturn"  | LogLevel.INFO | LogLevel.OFF  || -1
        "intReturn"  | LogLevel.OFF  | LogLevel.INFO || 2
        "intReturn"  | LogLevel.INFO | LogLevel.INFO || 2
        "wideReturn" | LogLevel.OFF  | LogLevel.INFO || 3
        "wideReturn" | LogLevel.INFO | LogLevel.INFO || 3
    }

    def "consume allocates loggerSlot when either enter or exit logging is active and skips it when both are off"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, enter, exit, "", false)

        then:
        plan.loggerSlot() == expectedLoggerSlot

        where:
        methodName   | enter         | exit          || expectedLoggerSlot
        "voidNoArgs" | LogLevel.OFF  | LogLevel.OFF  || -1
        "voidNoArgs" | LogLevel.INFO | LogLevel.OFF  || 1
        "voidNoArgs" | LogLevel.OFF  | LogLevel.INFO || 1
        "voidNoArgs" | LogLevel.INFO | LogLevel.INFO || 1
        "intReturn"  | LogLevel.INFO | LogLevel.INFO || 3
        "intReturn"  | LogLevel.OFF  | LogLevel.INFO || 3
        "intReturn"  | LogLevel.INFO | LogLevel.OFF  || 2
        "wideReturn" | LogLevel.INFO | LogLevel.INFO || 5
    }

    def "consume shiftAmount equals the sum of slot widths allocated above firstLocal"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, enter, exit, "", false)

        then:
        plan.shiftAmount() == expectedShift

        where:
        methodName   | enter         | exit          || expectedShift
        "voidNoArgs" | LogLevel.OFF  | LogLevel.OFF  || 0
        "voidNoArgs" | LogLevel.INFO | LogLevel.OFF  || 1
        "voidNoArgs" | LogLevel.OFF  | LogLevel.INFO || 1
        "intReturn"  | LogLevel.OFF  | LogLevel.INFO || 2
        "intReturn"  | LogLevel.INFO | LogLevel.INFO || 2
        "intReturn"  | LogLevel.INFO | LogLevel.OFF  || 1
        "wideReturn" | LogLevel.INFO | LogLevel.INFO || 3
        "wideReturn" | LogLevel.OFF  | LogLevel.INFO || 3
    }

    def "consume creates exitEpilogueLabel only when exit logging is active"() {
        given:
        def method = fixtureMethod("voidNoArgs")

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, enter, exit, "", false)

        then:
        (plan.exitEpilogueLabel() != null) == labelExpected

        where:
        enter         | exit          || labelExpected
        LogLevel.OFF  | LogLevel.OFF  || false
        LogLevel.INFO | LogLevel.OFF  || false
        LogLevel.OFF  | LogLevel.INFO || true
        LogLevel.INFO | LogLevel.INFO || true
    }

    def "consume builds syntheticEnterName by prefixing methodName with the enter prefix and appending the overload suffix"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, LogLevel.INFO, LogLevel.INFO, overloadSuffix, false)

        then:
        plan.syntheticEnterName() == expected

        where:
        methodName   | overloadSuffix || expected
        "voidNoArgs" | ""             || WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "voidNoArgs"
        "voidNoArgs" | "\$1"          || WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "voidNoArgs" + "\$1"
        "intReturn"  | "\$xyz"        || WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "intReturn" + "\$xyz"
    }

    def "consume builds syntheticExitName by prefixing methodName with the exit prefix and appending the overload suffix"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, LogLevel.INFO, LogLevel.INFO, overloadSuffix, false)

        then:
        plan.syntheticExitName() == expected

        where:
        methodName   | overloadSuffix || expected
        "voidNoArgs" | ""             || WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "voidNoArgs"
        "voidNoArgs" | "\$1"          || WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "voidNoArgs" + "\$1"
        "intReturn"  | "\$xyz"        || WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "intReturn" + "\$xyz"
    }

    def "consume delegates syntheticEnterDescriptor to SyntheticMethodEmitter.buildSyntheticEnterDescriptor"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, LogLevel.INFO, LogLevel.OFF, "", false)

        then:
        plan.syntheticEnterDescriptor() == SyntheticMethodEmitter.buildSyntheticEnterDescriptor(method)

        where:
        methodName << ["voidNoArgs", "intReturn", "wideReturn", "mixedWide", "staticNoArgs"]
    }

    def "consume passes returnType to syntheticExitDescriptor when returnIgnored is false and VOID when returnIgnored is true"() {
        given:
        def method = fixtureMethod(methodName)
        def expectedExitReturn = returnIgnored ? Type.VOID_TYPE : Type.getReturnType(method.getDescriptor())

        when:
        def plan = LightweightInjectionPlanConsumer.consume(method, LogLevel.OFF, LogLevel.INFO, "", returnIgnored)

        then:
        plan.syntheticExitDescriptor() == SyntheticMethodEmitter.buildSyntheticExitDescriptor(expectedExitReturn)

        where:
        methodName   | returnIgnored
        "voidNoArgs" | false
        "voidNoArgs" | true
        "intReturn"  | false
        "intReturn"  | true
        "wideReturn" | false
        "wideReturn" | true
    }

    def "consume propagates returnIgnored flag into the plan"() {
        given:
        def method = fixtureMethod("intReturn")

        expect:
        LightweightInjectionPlanConsumer.consume(method, LogLevel.INFO, LogLevel.INFO, "", flag).returnIgnored() == flag

        where:
        flag << [true, false]
    }

    def "isEnabledMethodForLevel returns the correct check method name for each active level"() {
        expect:
        LightweightInjectionPlanConsumer.isEnabledMethodForLevel(level) == expected

        where:
        level            || expected
        LogLevel.TRACE   || "isTraceEnabled"
        LogLevel.DEBUG   || "isDebugEnabled"
        LogLevel.INFO    || "isInfoEnabled"
        LogLevel.WARN    || "isWarnEnabled"
        LogLevel.ERROR   || "isErrorEnabled"
    }

    def "isEnabledMethodForLevel throws IllegalStateException carrying level-check guard message for OFF"() {
        when:
        LightweightInjectionPlanConsumer.isEnabledMethodForLevel(LogLevel.OFF)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "OFF level should be skipped before level check"
    }

    def "fluentAtLevelMethod returns the correct fluent builder method name for each active level"() {
        expect:
        LightweightInjectionPlanConsumer.fluentAtLevelMethod(level) == expected

        where:
        level            || expected
        LogLevel.TRACE   || "atTrace"
        LogLevel.DEBUG   || "atDebug"
        LogLevel.INFO    || "atInfo"
        LogLevel.WARN    || "atWarn"
        LogLevel.ERROR   || "atError"
    }

    def "fluentAtLevelMethod throws IllegalStateException carrying fluent-invocation guard message for OFF"() {
        when:
        LightweightInjectionPlanConsumer.fluentAtLevelMethod(LogLevel.OFF)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "OFF level should be skipped before fluent invocation"
    }
}
