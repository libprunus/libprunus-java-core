package org.libprunus.core.plugin.aot.log

import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import spock.lang.Specification

class SyntheticValueAppenderSpec extends Specification {

    def "private constructor throws UnsupportedOperationException to enforce non-instantiability"() {
        when:
        new SyntheticValueAppender()

        then:
        thrown(UnsupportedOperationException)
    }

    def "appendPrimitive emits ALOAD context, primitive load valueSlot, INVOKEVIRTUAL append on context, and POP to discard truncation flag"() {
        given:
        def recording = new ContextInvocationRecordingMethodVisitor()

        when:
        SyntheticValueAppender.appendPrimitive(recording, 0, type, 1)

        then:
        recording.runtimeAppendCalls == ["append"]
        recording.runtimeAppendDescriptors == [expectedDescriptor]
        recording.aloadVars == [0]
        recording.primitiveLoadVars == [1]
        recording.popCount == 1

        and:
        !recording.runtimeAppendCalls.contains("render")
        recording.lengthInvokeCount == 0
        recording.pop2Count == 0
        recording.invokeStaticCount == 0
        recording.invokeInterfaceCount == 0
        recording.invokeSpecialCount == 0
        recording.astoreVars.isEmpty()
        recording.ifeqCount == 0
        recording.labelCount == 0
        recording.tryCatchBlockCount == 0
        recording.ldcValues.isEmpty()

        where:
        type              || expectedDescriptor
        Type.BOOLEAN_TYPE || "(Z)Z"
        Type.BYTE_TYPE    || "(I)Z"
        Type.CHAR_TYPE    || "(C)Z"
        Type.SHORT_TYPE   || "(I)Z"
        Type.INT_TYPE     || "(I)Z"
        Type.LONG_TYPE    || "(J)Z"
        Type.FLOAT_TYPE   || "(F)Z"
        Type.DOUBLE_TYPE  || "(D)Z"
    }

    def "appendRender emits ALOAD context, ALOAD valueSlot, and INVOKEVIRTUAL render without POP or primitive load"() {
        given:
        def recording = new ContextInvocationRecordingMethodVisitor()

        when:
        SyntheticValueAppender.appendRender(recording, 3, 5)

        then:
        recording.runtimeAppendCalls == ["render"]
        recording.runtimeAppendDescriptors == ["(Ljava/lang/Object;)V"]
        recording.aloadVars == [3, 5]

        and:
        !recording.runtimeAppendCalls.contains("append")
        recording.lengthInvokeCount == 0
        recording.primitiveLoadVars.isEmpty()
        recording.popCount == 0
        recording.pop2Count == 0
        recording.invokeStaticCount == 0
        recording.invokeInterfaceCount == 0
        recording.invokeSpecialCount == 0
        recording.astoreVars.isEmpty()
        recording.ifeqCount == 0
        recording.labelCount == 0
        recording.tryCatchBlockCount == 0
        recording.ldcValues.isEmpty()
    }

    private static class ContextInvocationRecordingMethodVisitor extends MethodVisitor {
        int lengthInvokeCount = 0
        int popCount = 0
        int pop2Count = 0
        int invokeStaticCount = 0
        int invokeInterfaceCount = 0
        int invokeSpecialCount = 0
        int ifeqCount = 0
        int labelCount = 0
        int tryCatchBlockCount = 0
        List<Integer> iloadVars = []
        List<Integer> primitiveLoadVars = []
        List<Integer> aloadVars = []
        List<Integer> astoreVars = []
        List<String> runtimeAppendCalls = []
        List<String> runtimeAppendDescriptors = []
        List<Object> ldcValues = []

        ContextInvocationRecordingMethodVisitor() { super(Opcodes.ASM9) }

        @Override
        void visitVarInsn(int opcode, int varIndex) {
            if (opcode == Opcodes.ALOAD) {
                aloadVars << varIndex
            }
            if (opcode == Opcodes.ASTORE) {
                astoreVars << varIndex
            }
            if (opcode == Opcodes.ILOAD) {
                iloadVars << varIndex
            }
            if (opcode == Opcodes.ILOAD
                    || opcode == Opcodes.LLOAD
                    || opcode == Opcodes.FLOAD
                    || opcode == Opcodes.DLOAD) {
                primitiveLoadVars << varIndex
            }
        }

        @Override
        void visitInsn(int opcode) {
            if (opcode == Opcodes.POP) {
                popCount++
            }
            if (opcode == Opcodes.POP2) {
                pop2Count++
            }
        }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/StringBuilder" && name == "length") {
                lengthInvokeCount++
            }
            if (opcode == Opcodes.INVOKEVIRTUAL && owner == AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME) {
                runtimeAppendCalls << name
                runtimeAppendDescriptors << descriptor
            }
            if (opcode == Opcodes.INVOKESTATIC) {
                invokeStaticCount++
            }
            if (opcode == Opcodes.INVOKEINTERFACE) {
                invokeInterfaceCount++
            }
            if (opcode == Opcodes.INVOKESPECIAL) {
                invokeSpecialCount++
            }
        }

        @Override
        void visitJumpInsn(int opcode, Label label) {
            if (opcode == Opcodes.IFEQ) {
                ifeqCount++
            }
        }

        @Override
        void visitLabel(Label label) {
            labelCount++
        }

        @Override
        void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            tryCatchBlockCount++
        }

        @Override
        void visitLdcInsn(Object value) {
            ldcValues << value
        }
    }
}
