package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class ClassPlanAssemblerAlgorithmSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(AlgorithmFixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static MethodNode buildNode(MethodDescription method, List<Family> parameterFamilies, Family returnFamily) {
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

    private static List<Family> buildFamilies(int count, Collection<Integer> suppressIndices, Collection<Integer> maskIndices) {
        def result = new ArrayList<Family>(count)
        for (int i = 0; i < count; i++) {
            if (suppressIndices.contains(i)) {
                result.add(Family.SUPPRESS)
            } else if (maskIndices.contains(i)) {
                result.add(Family.MASK)
            } else {
                result.add(Family.NONE)
            }
        }
        result
    }

    def "assembleFromRouteGraph routes parameterFamilies to ignored vs masked bitmasks across single-segment combinations"() {
        given:
        def method = fixtureMethod("anchor")
        def node = buildNode(method, parameterFamilies, Family.NONE)

        when:
        def plan = ClassPlanAssembler.assembleFromRouteGraph(method, node, LogLevel.INFO, LogLevel.INFO)

        then:
        plan.ignoredParamMask() as List == expectedIgnored as List
        plan.maskedParamMask() as List == expectedMasked as List

        where:
        parameterFamilies                                                || expectedIgnored | expectedMasked
        []                                                               || []              | []
        [Family.NONE]                                                    || [0L]            | [0L]
        [Family.SUPPRESS]                                                || [1L]            | [0L]
        [Family.MASK]                                                    || [0L]            | [1L]
        [Family.PASS_THROUGH]                                            || [0L]            | [0L]
        [Family.SUPPRESS, Family.SUPPRESS, Family.SUPPRESS]              || [0b111L]        | [0L]
        [Family.MASK, Family.MASK, Family.MASK]                          || [0L]            | [0b111L]
        [Family.SUPPRESS, Family.MASK, Family.SUPPRESS, Family.MASK]     || [0b0101L]       | [0b1010L]
        [Family.PASS_THROUGH, Family.NONE, Family.SUPPRESS, Family.MASK] || [0b0100L]       | [0b1000L]
    }

    def "assembleFromRouteGraph distributes ignore vs mask bits across multi-segment bitsets for parameter counts crossing 64"() {
        given:
        def method = fixtureMethod("anchor")
        def families = buildFamilies(paramCount, suppressIndices, maskIndices)
        def node = buildNode(method, families, Family.NONE)

        when:
        def plan = ClassPlanAssembler.assembleFromRouteGraph(method, node, LogLevel.INFO, LogLevel.INFO)

        then:
        plan.ignoredParamMask().length == expectedSegments
        plan.maskedParamMask().length == expectedSegments
        suppressIndices.every { plan.isParamIgnored(it as int) }
        maskIndices.every { plan.isParamMasked(it as int) }
        (0..<paramCount).findAll { !suppressIndices.contains(it) }.every { !plan.isParamIgnored(it as int) }
        (0..<paramCount).findAll { !maskIndices.contains(it) }.every { !plan.isParamMasked(it as int) }

        where:
        paramCount | suppressIndices | maskIndices || expectedSegments
        64         | [0, 63]         | [1, 62]     || 1
        65         | [0, 64]         | [1, 63]     || 2
        128        | [0, 64, 127]    | [63, 65]    || 2
        129        | [0, 128]        | [64, 127]   || 3
    }

    def "assembleFromRouteGraph projects MethodKey owner, name, descriptor across instance, static, void-return, mixed, generic-erasure, and array shapes"() {
        given:
        def method = fixtureMethod(methodName)
        def node = buildNode(method, parameterFamilies, Family.NONE)

        when:
        def plan = ClassPlanAssembler.assembleFromRouteGraph(method, node, LogLevel.INFO, LogLevel.INFO)

        then:
        plan.methodKey().ownerInternalName() == FIXTURE_TYPE.getInternalName()
        plan.methodKey().methodName() == methodName
        plan.methodKey().methodDescriptor() == expectedDescriptor

        where:
        methodName    | parameterFamilies                       || expectedDescriptor
        "anchor"      | []                                      || "()V"
        "staticVoid"  | [Family.NONE]                           || "(I)V"
        "mixed"       | [Family.NONE, Family.NONE, Family.NONE] || "(IJLjava/lang/Object;)Ljava/lang/String;"
        "genericPass" | [Family.NONE]                           || "(Ljava/lang/Object;)Ljava/lang/Object;"
        "arrayArgs"   | [Family.NONE, Family.NONE]              || "([Ljava/lang/String;[B)[B"
    }

    def "MethodPlan isParamIgnored exhaustively maps bit index to long-bitset semantics across null, empty, single-segment, multi-segment, and negative index"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/Algo", "exec", "()V")
        def plan = new ClassPlanAssembler.MethodPlan(methodKey, mask, null, false, false, LogLevel.INFO, LogLevel.INFO)

        expect:
        plan.isParamIgnored(index) == expected

        where:
        mask                     | index             || expected
        null                     | 0                 || false
        [] as long[]             | 0                 || false
        [0b10L] as long[]        | 0                 || false
        [0b10L] as long[]        | 1                 || true
        [0b1L] as long[]         | 64                || false
        [0b1L] as long[]         | 128               || false
        [0L, 0b1L] as long[]     | 63                || false
        [0L, 0b1L] as long[]     | 64                || true
        [0L, 0b1L] as long[]     | 65                || false
        [0L, 0L, 0b1L] as long[] | 128               || true
        null                     | -1                || false
        [0b1L] as long[]         | -1                || false
        [0b1L] as long[]         | Integer.MIN_VALUE || false
    }

    def "MethodPlan isParamMasked exhaustively maps bit index to long-bitset semantics across null, empty, single-segment, multi-segment, and negative index"() {
        given:
        def methodKey = new ClassPlanAssembler.MethodKey("sample/Algo", "exec", "()V")
        def plan = new ClassPlanAssembler.MethodPlan(methodKey, null, mask, false, false, LogLevel.INFO, LogLevel.INFO)

        expect:
        plan.isParamMasked(index) == expected

        where:
        mask                     | index             || expected
        null                     | 0                 || false
        [] as long[]             | 0                 || false
        [0b10L] as long[]        | 0                 || false
        [0b10L] as long[]        | 1                 || true
        [0b1L] as long[]         | 64                || false
        [0b1L] as long[]         | 128               || false
        [0L, 0b1L] as long[]     | 63                || false
        [0L, 0b1L] as long[]     | 64                || true
        [0L, 0b1L] as long[]     | 65                || false
        [0L, 0L, 0b1L] as long[] | 128               || true
        null                     | -1                || false
        [0b1L] as long[]         | -1                || false
        [0b1L] as long[]         | Integer.MIN_VALUE || false
    }

    @SuppressWarnings("unused")
    static class AlgorithmFixture {
        void anchor() {}

        static void staticVoid(int i) {}

        String mixed(int i, long j, Object o) { "" }

        <T> T genericPass(T t) { t }

        byte[] arrayArgs(String[] strings, byte[] bytes) { bytes }
    }
}
