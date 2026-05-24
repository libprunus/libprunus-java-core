package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

final class AsmEmitHelper {

    private AsmEmitHelper() {
        throw new UnsupportedOperationException();
    }

    static void appendString(MethodVisitor mv, int contextSlot, String value) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitLdcInsn(value);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "append",
                AsmDescriptors.CONTEXT_APPEND_TEXT_DESCRIPTOR,
                false);
        mv.visitInsn(Opcodes.POP);
    }

    static void markRenderTruncation(MethodVisitor mv, int contextSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "markRenderTruncation",
                AsmDescriptors.CONTEXT_MARK_RENDER_TRUNCATION_DESCRIPTOR,
                false);
    }

    static void isTruncated(MethodVisitor mv, int contextSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "isTruncated",
                AsmDescriptors.CONTEXT_IS_TRUNCATED_DESCRIPTOR,
                false);
    }
}
