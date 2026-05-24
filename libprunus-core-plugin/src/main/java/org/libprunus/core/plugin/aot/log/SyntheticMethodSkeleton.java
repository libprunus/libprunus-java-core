package org.libprunus.core.plugin.aot.log;

import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.libprunus.core.log.runtime.LogLevel;

final class SyntheticMethodSkeleton {

    private SyntheticMethodSkeleton() {
        throw new UnsupportedOperationException();
    }

    static String fqcnForHandler(String classInternalName, MethodDescription method) {
        return classInternalName.replace('/', '.') + "#" + method.getInternalName() + method.getDescriptor();
    }

    static void emitAtLevel(MethodVisitor mv, LogLevel level) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                AsmDescriptors.LOGGER_INTERNAL_NAME,
                LightweightInjectionPlanConsumer.fluentAtLevelMethod(level),
                AsmDescriptors.LOGGER_FLUENT_DESCRIPTOR,
                true);
    }

    static void emitEnrichInvocation(MethodVisitor mv, String classInternalName, int eventSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, eventSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                classInternalName,
                WeavingInternalNames.SYNTHETIC_ENRICH_METHOD,
                AsmDescriptors.ENRICH_METHOD_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ASTORE, eventSlot);
    }

    static void emitMarkRenderTruncationIfTruncated(MethodVisitor mv, int contextSlot) {
        Label skipMark = new Label();
        AsmEmitHelper.isTruncated(mv, contextSlot);
        mv.visitJumpInsn(Opcodes.IFEQ, skipMark);
        AsmEmitHelper.markRenderTruncation(mv, contextSlot);
        mv.visitLabel(skipMark);
    }

    static void logAndReleaseContext(MethodVisitor mv, int contextSlot, int eventSlot) {
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitVarInsn(Opcodes.ALOAD, eventSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "logAndRelease",
                AsmDescriptors.CONTEXT_LOG_AND_RELEASE_DESCRIPTOR,
                false);
    }

    interface BodyEmitter {
        void emit(MethodVisitor mv, int eventSlot, int contextSlot);
    }

    static void emitWithErrorBoundary(
            MethodVisitor mv,
            String classInternalName,
            MethodDescription method,
            LogLevel level,
            int eventSlot,
            int contextSlot,
            int exceptionSlot,
            List<FieldExtractorRef> fieldExtractors,
            BodyEmitter body) {
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitVarInsn(Opcodes.ASTORE, eventSlot);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitVarInsn(Opcodes.ASTORE, contextSlot);

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label handler = new Label();
        mv.visitTryCatchBlock(tryStart, tryEnd, handler, "java/lang/Throwable");

        mv.visitLabel(tryStart);
        emitAtLevel(mv, level);
        mv.visitVarInsn(Opcodes.ASTORE, eventSlot);
        if (!fieldExtractors.isEmpty()) {
            emitEnrichInvocation(mv, classInternalName, eventSlot);
        }
        body.emit(mv, eventSlot, contextSlot);
        emitMarkRenderTruncationIfTruncated(mv, contextSlot);

        logAndReleaseContext(mv, contextSlot, eventSlot);
        mv.visitLabel(tryEnd);
        mv.visitInsn(Opcodes.RETURN);

        mv.visitLabel(handler);
        mv.visitVarInsn(Opcodes.ASTORE, exceptionSlot);
        mv.visitLdcInsn(fqcnForHandler(classInternalName, method));
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitVarInsn(Opcodes.ALOAD, exceptionSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME,
                "handleRenderFailure",
                AsmDescriptors.RUNTIME_HANDLE_RENDER_FAILURE_DESCRIPTOR,
                false);
        mv.visitInsn(Opcodes.RETURN);
    }
}
