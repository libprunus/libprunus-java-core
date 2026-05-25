package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.matcher.ElementMatchers
import org.libprunus.core.log.runtime.LogLevel
import org.libprunus.core.plugin.aot.log.AotMethodLoggingTransformer.MethodLogContext
import spock.lang.Specification

class SyntheticEnterEmitterSpec extends Specification {

    private static final TypeDescription FIXTURE_TYPE = TypeDescription.ForLoadedType.of(Fixture)

    private static MethodDescription fixtureMethod(String name) {
        FIXTURE_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static ClassPlanAssembler.MethodPlan defaultMethodPlan(
            MethodDescription method, LogLevel enterLevel, LogLevel exitLevel) {
        int bitsetLength = (method.getParameters().size() + Long.SIZE - 1) >>> 6
        def methodKey = new ClassPlanAssembler.MethodKey(
                method.getDeclaringType().asErasure().getInternalName(),
                method.getInternalName(),
                method.getDescriptor())
        new ClassPlanAssembler.MethodPlan(
                methodKey, new long[bitsetLength], new long[bitsetLength], false, false, enterLevel, exitLevel)
    }

    def "private constructor throws UnsupportedOperationException to enforce non-instantiability"() {
        when:
        new SyntheticEnterEmitter()

        then:
        thrown(UnsupportedOperationException)
    }

    def "emit emits no method when enterLogLevel is OFF"() {
        given:
        def recording = new MethodAndInstructionCountingClassVisitor()
        def method = fixtureMethod("intParam")
        def ctx = new MethodLogContext("Fixture", "intParam", LogLevel.OFF, LogLevel.INFO)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, LogLevel.OFF, LogLevel.INFO), ctx, "")

        when:
        SyntheticEnterEmitter.emit(recording, "test/Fixture", request, [])

        then:
        recording.visitMethodCount == 0
        recording.totalInstructionCount == 0
    }

    def "emit emits private static synthetic method with enter prefix when enterLogLevel is non-OFF"() {
        given:
        def recording = new MethodRecordingClassVisitor()
        def method = fixtureMethod("intParam")
        def ctx = new MethodLogContext("Fixture", "intParam", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, LogLevel.INFO, LogLevel.OFF), ctx, "")

        when:
        SyntheticEnterEmitter.emit(recording, "test/Fixture", request, [])

        then:
        recording.lastMethodName == WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "intParam"
        recording.lastMethodAccess == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)
    }

    def "emit appends overloadSuffix verbatim after method internal name in synthetic method name"() {
        given:
        def recording = new MethodRecordingClassVisitor()
        def method = fixtureMethod("intParam")
        def ctx = new MethodLogContext("Fixture", "intParam", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, LogLevel.INFO, LogLevel.OFF), ctx, '$java_lang_String')

        when:
        SyntheticEnterEmitter.emit(recording, "test/Fixture", request, [])

        then:
        recording.lastMethodName == WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + "intParam" + '$java_lang_String'
    }

    def "emit computes eventSlot as one plus paramSlotCount and contextSlot as eventSlot plus one"() {
        given:
        def recording = new AstoreCapturingClassVisitor()
        def method = fixtureMethod(methodName)
        def ctx = new MethodLogContext("Fixture", methodName, LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, LogLevel.INFO, LogLevel.OFF), ctx, "")
        int expectedEventSlot = 1 + expectedParamSlotCount
        int expectedContextSlot = expectedEventSlot + 1
        int expectedExceptionSlot = expectedContextSlot + 1

        when:
        SyntheticEnterEmitter.emit(recording, "test/Fixture", request, [])

        then:
        recording.astoreVars.contains(expectedEventSlot)
        recording.astoreVars.contains(expectedContextSlot)
        recording.astoreVars.contains(expectedExceptionSlot)

        where:
        methodName        || expectedParamSlotCount
        "voidNoArgs"      || 0
        "intParam"        || 1
        "longReturn"      || 2
        "multiParams"     || 4
    }

    def "emitEnterBody folds consecutive masked parameters into a single acquireWithPrefix constant with no additional append calls"() {
        given:
        def ldcValues = []
        def methodCalls = []
        def captureMv = new LdcAndMethodCallCapturingMethodVisitor(ldcValues, methodCalls)
        def params = [
                new MethodEnterParameterPlanConsumer.EnterParamPlan("from", Type.getType(String), 1, true),
                new MethodEnterParameterPlanConsumer.EnterParamPlan("to", Type.getType(String), 2, true)
        ]
        def ctx = new MethodLogContext("Service", "transfer", LogLevel.INFO, LogLevel.INFO)

        when:
        SyntheticEnterEmitter."emitEnterBody"(captureMv, 3, params, ctx)

        then:
        methodCalls.count { it == "acquireWithPrefix" } == 1
        methodCalls.count { it == "append" } == 0

        and:
        ldcValues.any { it instanceof String && it.contains("from=***") && it.endsWith(", to=***)") }
    }

    def "emitEnterBody acquires on first unmasked param then appends suffix constants for trailing masked params"() {
        given:
        def ldcValues = []
        def methodCalls = []
        def captureMv = new LdcAndMethodCallCapturingMethodVisitor(ldcValues, methodCalls)
        def params = [
                new MethodEnterParameterPlanConsumer.EnterParamPlan("a", Type.getType(String), 1, false),
                new MethodEnterParameterPlanConsumer.EnterParamPlan("b", Type.getType(String), 2, true)
        ]
        def ctx = new MethodLogContext("Service", "m", LogLevel.INFO, LogLevel.INFO)

        when:
        SyntheticEnterEmitter."emitEnterBody"(captureMv, 3, params, ctx)

        then:
        def acquireIdx = methodCalls.findIndexOf { it == "acquireWithPrefix" }
        def firstAppendIdx = methodCalls.findIndexOf { it == "append" }
        acquireIdx >= 0
        firstAppendIdx > acquireIdx

        and:
        ldcValues.any { it instanceof String && it.startsWith("|> [ENTER] Service.m(") && it.endsWith("a=") }
        ldcValues.any { it instanceof String && it.contains("b=***") && it.endsWith(")") }
    }

    def "emitEnterBody acquires once with first param then emits append-value pairs per remaining unmasked param ending with closing paren append"() {
        given:
        def ldcValues = []
        def methodCalls = []
        def captureMv = new LdcAndMethodCallCapturingMethodVisitor(ldcValues, methodCalls)
        def params = [
                new MethodEnterParameterPlanConsumer.EnterParamPlan("a", Type.INT_TYPE, 1, false),
                new MethodEnterParameterPlanConsumer.EnterParamPlan("b", Type.getType(String), 2, false),
                new MethodEnterParameterPlanConsumer.EnterParamPlan("c", Type.DOUBLE_TYPE, 3, false)
        ]
        def ctx = new MethodLogContext("Svc", "m", LogLevel.INFO, LogLevel.INFO)

        when:
        SyntheticEnterEmitter."emitEnterBody"(captureMv, 4, params, ctx)

        then:
        methodCalls.count { it == "acquireWithPrefix" } == 1
        methodCalls.count { it == "append" } == 5
        methodCalls.count { it == "render" } == 1

        and:
        ldcValues.any { it instanceof String && it == "|> [ENTER] Svc.m(a=" }
        ldcValues.contains(", b=")
        ldcValues.contains(", c=")
        ldcValues.contains(")")
    }

    def "emitEnterBody emits single acquireWithPrefix with closing paren and no append calls when params list is empty"() {
        given:
        def ldcValues = []
        def methodCalls = []
        def captureMv = new LdcAndMethodCallCapturingMethodVisitor(ldcValues, methodCalls)
        def ctx = new MethodLogContext("Svc", "noargs", LogLevel.INFO, LogLevel.INFO)

        when:
        SyntheticEnterEmitter."emitEnterBody"(captureMv, 1, [], ctx)

        then:
        methodCalls.count { it == "acquireWithPrefix" } == 1
        methodCalls.count { it == "append" } == 0
        ldcValues.contains("|> [ENTER] Svc.noargs()")
    }

    def "emitEnterBody defers acquire until first unmasked param when leading params are masked accumulating masked tokens into the prefix"() {
        given:
        def ldcValues = []
        def methodCalls = []
        def captureMv = new LdcAndMethodCallCapturingMethodVisitor(ldcValues, methodCalls)
        def params = [
                new MethodEnterParameterPlanConsumer.EnterParamPlan("a", Type.getType(String), 1, true),
                new MethodEnterParameterPlanConsumer.EnterParamPlan("b", Type.INT_TYPE, 2, false)
        ]
        def ctx = new MethodLogContext("Svc", "m", LogLevel.INFO, LogLevel.INFO)

        when:
        SyntheticEnterEmitter."emitEnterBody"(captureMv, 3, params, ctx)

        then:
        methodCalls.count { it == "acquireWithPrefix" } == 1
        ldcValues.any {
            it instanceof String && it.startsWith("|> [ENTER] Svc.m(") && it.contains("a=***") && it.endsWith("b=")
        }
    }

    def "emitParameterValue routes Type sort below ARRAY to appendPrimitive and ARRAY or above to appendRender"() {
        given:
        def methodCalls = []
        def captureMv = new MethodCallCapturingMethodVisitor(methodCalls)

        when:
        SyntheticEnterEmitter."emitParameterValue"(
                captureMv, 2, new MethodEnterParameterPlanConsumer.EnterParamPlan("p", type, 1, false))

        then:
        methodCalls.any { it.name == expectedName }
        !methodCalls.any { it.name == forbiddenName }

        where:
        type                    || expectedName | forbiddenName
        Type.INT_TYPE           || "append"     | "render"
        Type.LONG_TYPE          || "append"     | "render"
        Type.DOUBLE_TYPE        || "append"     | "render"
        Type.getType(String)    || "render"     | "append"
        Type.getType(int[])     || "render"     | "append"
        Type.getType(Object[])  || "render"     | "append"
    }

    def "emitAcquireWithPrefix emits LDC prefix then INVOKESTATIC StringBuilderPool acquireWithPrefix then ASTORE contextSlot"() {
        given:
        def mv = new InstructionRecordingMethodVisitor()

        when:
        SyntheticEnterEmitter."emitAcquireWithPrefix"(mv, contextSlot, "anyPrefix")

        then:
        mv.events == [
                [kind: 'ldc', value: "anyPrefix"],
                [kind: 'method', op: Opcodes.INVOKESTATIC,
                 owner: WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME,
                 name: "acquireWithPrefix",
                 desc: AsmDescriptors.ACQUIRE_WITH_PREFIX_DESCRIPTOR, itf: false],
                [kind: 'var', op: Opcodes.ASTORE, slot: contextSlot]
        ]

        where:
        contextSlot << [0, 1, 5, 255]
    }

    def "computeParameterSlotCount sums parameter slot sizes treating long and double as two and all other types as one"() {
        given:
        def method = fixtureMethod(methodName)

        when:
        def result = SyntheticEnterEmitter."computeParameterSlotCount"(method)

        then:
        result == expectedSlotCount

        where:
        methodName        || expectedSlotCount
        "voidNoArgs"      || 0
        "intParam"        || 1
        "multiParams"     || 4
        "longReturn"      || 2
        "staticIntReturn" || 1
    }

    private static class MethodRecordingClassVisitor extends ClassVisitor {
        int lastMethodAccess = -1
        String lastMethodName = null

        MethodRecordingClassVisitor() { super(Opcodes.ASM9) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            lastMethodAccess = access
            lastMethodName = name
            new MethodVisitor(Opcodes.ASM9) {}
        }
    }

    private static class MethodAndInstructionCountingClassVisitor extends ClassVisitor {
        int visitMethodCount = 0
        int totalInstructionCount = 0

        MethodAndInstructionCountingClassVisitor() { super(Opcodes.ASM9) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            visitMethodCount++
            new MethodVisitor(Opcodes.ASM9) {
                @Override
                void visitInsn(int op) { totalInstructionCount++ }
                @Override
                void visitVarInsn(int op, int v) { totalInstructionCount++ }
                @Override
                void visitIntInsn(int op, int operand) { totalInstructionCount++ }
                @Override
                void visitTypeInsn(int op, String type) { totalInstructionCount++ }
                @Override
                void visitLdcInsn(Object value) { totalInstructionCount++ }
                @Override
                void visitJumpInsn(int op, Label label) { totalInstructionCount++ }
                @Override
                void visitMethodInsn(int op, String owner, String n, String d, boolean i) { totalInstructionCount++ }
                @Override
                void visitFieldInsn(int op, String owner, String n, String d) { totalInstructionCount++ }
            }
        }
    }

    private static class AstoreCapturingClassVisitor extends ClassVisitor {
        final List<Integer> astoreVars = []

        AstoreCapturingClassVisitor() { super(Opcodes.ASM9) }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            new MethodVisitor(Opcodes.ASM9) {
                @Override
                void visitVarInsn(int op, int v) {
                    if (op == Opcodes.ASTORE) {
                        astoreVars << v
                    }
                }
            }
        }
    }

    private static class LdcAndMethodCallCapturingMethodVisitor extends MethodVisitor {
        private final List ldcSink
        private final List methodSink

        LdcAndMethodCallCapturingMethodVisitor(List ldcSink, List methodSink) {
            super(Opcodes.ASM9)
            this.ldcSink = ldcSink
            this.methodSink = methodSink
        }

        @Override
        void visitLdcInsn(Object value) { ldcSink << value }

        @Override
        void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            methodSink << name
        }
    }

    private static class MethodCallCapturingMethodVisitor extends MethodVisitor {
        private final List<Map> sink

        MethodCallCapturingMethodVisitor(List<Map> sink) {
            super(Opcodes.ASM9)
            this.sink = sink
        }

        @Override
        void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            sink << [op: op, owner: owner, name: name, desc: desc, itf: itf]
        }
    }

    private static class InstructionRecordingMethodVisitor extends MethodVisitor {
        final List<Map> events = []

        InstructionRecordingMethodVisitor() { super(Opcodes.ASM9) }

        @Override
        void visitVarInsn(int op, int slot) {
            events << [kind: 'var', op: op, slot: slot]
        }

        @Override
        void visitLdcInsn(Object value) {
            events << [kind: 'ldc', value: value]
        }

        @Override
        void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            events << [kind: 'method', op: op, owner: owner, name: name, desc: desc, itf: itf]
        }

        @Override
        void visitInsn(int op) {
            events << [kind: 'insn', op: op]
        }
    }

    @SuppressWarnings("unused")
    static class Fixture {
        void voidNoArgs() {}

        void intParam(int x) {}

        void multiParams(String s, int i, double d) {}

        static int staticIntReturn(int x) { return x }

        long longReturn(double d) { return 0L }
    }
}
