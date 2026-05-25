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

class SyntheticExitEmitterSpec extends Specification {

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
        new SyntheticExitEmitter()

        then:
        thrown(UnsupportedOperationException)
    }

    def "emit emits no method when exitLogLevel is OFF"() {
        given:
        def recording = new MethodAndInstructionCountingClassVisitor()
        def method = fixtureMethod("staticIntReturn")
        def ctx = new MethodLogContext("Fixture", "staticIntReturn", LogLevel.INFO, LogLevel.OFF)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, LogLevel.INFO, LogLevel.OFF), ctx, "")

        when:
        SyntheticExitEmitter.emit(recording, "test/Fixture", request, [])

        then:
        recording.visitMethodCount == 0
        recording.totalInstructionCount == 0
    }

    def "emit emits private static synthetic method with exit prefix when exitLogLevel is non-OFF"() {
        given:
        def recording = new MethodRecordingClassVisitor()
        def method = fixtureMethod("staticIntReturn")
        def ctx = new MethodLogContext("Fixture", "staticIntReturn", LogLevel.OFF, LogLevel.INFO)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, LogLevel.OFF, LogLevel.INFO), ctx, "")

        when:
        SyntheticExitEmitter.emit(recording, "test/Fixture", request, [])

        then:
        recording.lastMethodName == WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "staticIntReturn"
        recording.lastMethodAccess == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)
    }

    def "emit appends overloadSuffix verbatim after method internal name in synthetic method name"() {
        given:
        def recording = new MethodRecordingClassVisitor()
        def method = fixtureMethod("staticIntReturn")
        def ctx = new MethodLogContext("Fixture", "staticIntReturn", LogLevel.OFF, LogLevel.INFO)
        def request = new AotMethodLoggingTransformer.SyntheticMethodRequest(
                method, defaultMethodPlan(method, LogLevel.OFF, LogLevel.INFO), ctx, '$java_lang_String')

        when:
        SyntheticExitEmitter.emit(recording, "test/Fixture", request, [])

        then:
        recording.lastMethodName == WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + "staticIntReturn" + '$java_lang_String'
    }

    def "emitExitBody encodes void return as single acquireWithPrefix with closing paren and no append or return-slot read"() {
        given:
        def recording = new ExitBodyRecordingMethodVisitor()
        def ctx = new MethodLogContext("Fixture", "voidNoArgs", LogLevel.OFF, LogLevel.INFO)

        when:
        SyntheticExitEmitter."emitExitBody"(recording, 1, Type.VOID_TYPE, false, ctx)

        then:
        recording.methodCalls.count { it == "acquireWithPrefix" } == 1
        recording.methodCalls.count { it == "append" } == 0
        recording.ldcValues.any {
            it instanceof String && it.startsWith("|< [EXIT] Fixture.voidNoArgs(") && it.endsWith("()")
        }

        and:
        !recording.varInsns.any { it[0] == Opcodes.ILOAD }
        !recording.varInsns.any { it[0] == Opcodes.LLOAD }
        !recording.varInsns.any { it[0] == Opcodes.FLOAD }
        !recording.varInsns.any { it[0] == Opcodes.DLOAD }
    }

    def "emitExitBody encodes masked return value as static constant rather than reading the return slot"() {
        given:
        def recording = new ExitBodyRecordingMethodVisitor()
        def ctx = new MethodLogContext("Fixture", "staticIntReturn", LogLevel.OFF, LogLevel.INFO)

        when:
        SyntheticExitEmitter."emitExitBody"(recording, 1, Type.INT_TYPE, true, ctx)

        then:
        recording.ldcValues.any {
            it instanceof String && it.startsWith("|< [EXIT] Fixture.staticIntReturn(") && it.contains("value=***)")
        }

        and:
        !recording.varInsns.any { it[0] == Opcodes.ILOAD && it[1] == 1 }
    }

    def "emitExitBody emits acquirePrefix value=, primitive append, and closing paren append for unmasked primitive return"() {
        given:
        def recording = new ExitBodyRecordingMethodVisitor()
        def ctx = new MethodLogContext("Fixture", "m", LogLevel.OFF, LogLevel.INFO)

        when:
        SyntheticExitEmitter."emitExitBody"(recording, 2, Type.INT_TYPE, false, ctx)

        then:
        recording.ldcValues.any {
            it instanceof String && it.startsWith("|< [EXIT] Fixture.m(") && it.endsWith("value=")
        }
        recording.ldcValues.any { it == ")" }

        and:
        recording.varInsns.any { it[0] == Opcodes.ILOAD && it[1] == 1 }
        recording.methodCalls.count { it == "acquireWithPrefix" } == 1
        recording.methodCalls.count { it == "append" } == 2
    }

    private static class ExitBodyRecordingMethodVisitor extends MethodVisitor {
        final List ldcValues = []
        final List<List> varInsns = []
        final List<String> methodCalls = []

        ExitBodyRecordingMethodVisitor() { super(Opcodes.ASM9) }

        @Override
        void visitLdcInsn(Object value) { ldcValues << value }

        @Override
        void visitVarInsn(int op, int v) { varInsns << [op, v] }

        @Override
        void visitMethodInsn(int op, String owner, String name, String descriptor, boolean isInterface) {
            methodCalls << name
        }

        @Override
        void visitInsn(int op) {}

        @Override
        void visitMaxs(int ms, int ml) {}

        @Override
        void visitEnd() {}
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

    @SuppressWarnings("unused")
    static class Fixture {
        static int staticIntReturn(int x) { return x }
    }
}
