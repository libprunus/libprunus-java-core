package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class MethodEnterParameterPlanConsumerSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static ClassPlanAssembler.MethodPlan methodPlan(MethodDescription method, long[] ignoredMask, long[] maskedMask) {
        def methodKey = new ClassPlanAssembler.MethodKey(
                method.getDeclaringType().asErasure().getInternalName(),
                method.getInternalName(),
                method.getDescriptor())
        new ClassPlanAssembler.MethodPlan(methodKey, ignoredMask, maskedMask, false, false, LogLevel.INFO, LogLevel.INFO)
    }

    def "consume returns empty list for a method without parameters"() {
        given:
        def method = fixtureMethod("voidNoArgs")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [] as long[], [] as long[]))

        then:
        params.isEmpty()
    }

    def "consume maps ignored and masked bitmasks to syntheticSlot, type and masked components for multi-parameter methods"() {
        given:
        def method = fixtureMethod("multiParams")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method,
                methodPlan(method, ignored as long[], masked as long[]))

        then:
        params*.syntheticSlot() == expectedSlots
        params*.masked() == expectedMasked
        params*.type() == expectedTypes

        where:
        ignored | masked || expectedSlots || expectedMasked          || expectedTypes
        [0L]    | [0L]   || [1, 2, 3]     || [false, false, false]   || [Type.getType(String), Type.INT_TYPE, Type.DOUBLE_TYPE]
        []      | []     || [1, 2, 3]     || [false, false, false]   || [Type.getType(String), Type.INT_TYPE, Type.DOUBLE_TYPE]
        [2L]    | [0L]   || [1, 3]        || [false, false]          || [Type.getType(String), Type.DOUBLE_TYPE]
        [0L]    | [4L]   || [1, 2, 3]     || [false, false, true]    || [Type.getType(String), Type.INT_TYPE, Type.DOUBLE_TYPE]
    }

    def "consume advances slot by wide-type size even when the wide parameter is ignored"() {
        given:
        def method = fixtureMethod("ignoredWidePrefix")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [1L] as long[], [0L] as long[]))

        then:
        params*.syntheticSlot() == [3]
        params*.type() == [Type.INT_TYPE]
        params*.masked() == [false]
    }

    def "consume reserves slot 0 for the synthetic Logger and starts original parameter slots at 1 even for a static method"() {
        given:
        def method = fixtureMethod("staticTwoParams")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [0L] as long[], [0L] as long[]))

        then:
        params*.syntheticSlot() == [1, 2]
        params*.type() == [Type.INT_TYPE, Type.INT_TYPE]
    }

    def "consume sets EnterParamPlan name by delegating to AotMethodLoggingTransformer sanitizeForRecipe on the parameter name"() {
        given:
        def method = fixtureMethod("multiParams")
        def declaredNames = method.getParameters()*.getName()

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [0L] as long[], [0L] as long[]))

        then:
        params*.name() == declaredNames.collect { AotMethodLoggingTransformer.sanitizeForRecipe(it) }
    }

    def "consume preserves declared order of retained parameters when interleaved with ignored ones"() {
        given:
        def method = fixtureMethod("threeParamsForOrdering")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [2L] as long[], [0L] as long[]))

        then:
        params*.syntheticSlot() == [1, 3]
        params*.type() == [Type.getType(String), Type.DOUBLE_TYPE]
    }

    def "consume drops parameter when both ignored and masked bits are set for the same index"() {
        given:
        def method = fixtureMethod("multiParams")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [2L] as long[], [2L] as long[]))

        then:
        params*.syntheticSlot() == [1, 3]
        params*.masked() == [false, false]
    }

    def "consume handles multiple ignored and masked positions in the same parameter list"() {
        given:
        def method = fixtureMethod("wideMix")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [1L] as long[], [4L] as long[]))

        then:
        params*.syntheticSlot() == [3, 5]
        params*.type() == [Type.DOUBLE_TYPE, Type.INT_TYPE]
        params*.masked() == [false, true]
    }

    def "consume reads ignored and masked bits across long-word segment boundaries for parameter indices at and beyond 64"() {
        given:
        def method = fixtureMethod("sixtyFiveParams")
        def ignoredMask = [1L << 0, 1L << 0] as long[]
        def maskedMask = [0L, 0L] as long[]

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, ignoredMask, maskedMask))

        then:
        params.size() == 63
        params*.syntheticSlot()[0] == 2
        params*.syntheticSlot()[-1] == 64
    }

    def "consume builds EnterParamPlan type as the erased ASM Type for reference and array parameters"() {
        given:
        def method = fixtureMethod("refAndArray")

        when:
        def params = MethodEnterParameterPlanConsumer.consume(method, methodPlan(method, [0L] as long[], [0L] as long[]))

        then:
        params*.type() == [Type.getType(String), Type.getType(int[])]
        params*.syntheticSlot() == [1, 2]
    }

    @SuppressWarnings("unused")
    static class Fixture {
        void voidNoArgs() {}

        void multiParams(String s, int i, double d) {}

        void ignoredWidePrefix(long a, int b) {}

        static void staticTwoParams(int a, int b) {}

        void threeParamsForOrdering(String first, int second, double third) {}

        void refAndArray(String s, int[] arr) {}

        void wideMix(long a, double b, int c) {}

        void sixtyFiveParams(
                int p00, int p01, int p02, int p03, int p04, int p05, int p06, int p07,
                int p08, int p09, int p10, int p11, int p12, int p13, int p14, int p15,
                int p16, int p17, int p18, int p19, int p20, int p21, int p22, int p23,
                int p24, int p25, int p26, int p27, int p28, int p29, int p30, int p31,
                int p32, int p33, int p34, int p35, int p36, int p37, int p38, int p39,
                int p40, int p41, int p42, int p43, int p44, int p45, int p46, int p47,
                int p48, int p49, int p50, int p51, int p52, int p53, int p54, int p55,
                int p56, int p57, int p58, int p59, int p60, int p61, int p62, int p63,
                int p64) {}
    }
}
