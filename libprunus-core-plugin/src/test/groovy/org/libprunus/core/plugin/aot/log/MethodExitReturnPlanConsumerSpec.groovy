package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import spock.lang.Specification

class MethodExitReturnPlanConsumerSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static ClassPlanAssembler.MethodPlan methodPlan(MethodDescription method, boolean returnMasked, boolean returnIgnored = false) {
        def methodKey = new ClassPlanAssembler.MethodKey(
                method.getDeclaringType().asErasure().getInternalName(),
                method.getInternalName(),
                method.getDescriptor())
        int bitsetLength = (method.getParameters().size() + Long.SIZE - 1) >>> 6
        new ClassPlanAssembler.MethodPlan(methodKey, new long[bitsetLength], new long[bitsetLength], returnMasked, returnIgnored, LogLevel.INFO, LogLevel.INFO)
    }

    def "consume returns plan whose returnType reflects the method descriptor when returnIgnored is false"() {
        given:
        def method = fixtureMethod(fixtureName)

        when:
        def plan = MethodExitReturnPlanConsumer.consume(method, methodPlan(method, false))

        then:
        plan.returnType() == expectedType

        where:
        fixtureName    || expectedType
        "voidReturn"   || Type.VOID_TYPE
        "intReturn"    || Type.INT_TYPE
        "stringReturn" || Type.getType(String)
    }

    def "consume yields non-masked flag when MethodPlan.returnMasked is false"() {
        given:
        def method = fixtureMethod("intReturn")

        when:
        def plan = MethodExitReturnPlanConsumer.consume(method, methodPlan(method, false))

        then:
        plan.masked() == false
        plan.returnType() == Type.INT_TYPE
    }

    def "consume propagates masked=true verbatim and leaves returnType unchanged when returnIgnored is false"() {
        given:
        def method = fixtureMethod("intReturn")

        when:
        def plan = MethodExitReturnPlanConsumer.consume(method, methodPlan(method, true))

        then:
        plan.masked() == true
        plan.returnType() == Type.INT_TYPE
    }

    def "consume preserves masked flag verbatim even when descriptor return type is void"() {
        given:
        def method = fixtureMethod("voidReturn")

        when:
        def plan = MethodExitReturnPlanConsumer.consume(method, methodPlan(method, true))

        then:
        plan.returnType() == Type.VOID_TYPE
        plan.masked() == true
    }

    def "consume discards returnMasked input when returnIgnored is true"() {
        given:
        def method = fixtureMethod("stringReturn")

        when:
        def plan = MethodExitReturnPlanConsumer.consume(method, methodPlan(method, inputMasked, true))

        then:
        plan.returnType() == Type.VOID_TYPE
        plan.masked() == false

        where:
        inputMasked << [false, true]
    }

    @SuppressWarnings("unused")
    static class Fixture {
        void voidReturn() {}

        int intReturn() { 0 }

        String stringReturn() { null }
    }
}
