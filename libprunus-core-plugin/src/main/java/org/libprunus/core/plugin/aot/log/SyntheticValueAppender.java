package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

final class SyntheticValueAppender {

    private SyntheticValueAppender() {
        throw new UnsupportedOperationException();
    }

    static void appendPrimitive(MethodVisitor mv, int contextSlot, Type type, int valueSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), valueSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "append",
                AsmDescriptors.contextAppendPrimitiveDescriptor(type),
                false);
        mv.visitInsn(Opcodes.POP);
    }

    static void appendRender(MethodVisitor mv, int contextSlot, int valueSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitVarInsn(Opcodes.ALOAD, valueSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "render",
                AsmDescriptors.CONTEXT_APPEND_OBJECT_DESCRIPTOR,
                false);
    }
}
