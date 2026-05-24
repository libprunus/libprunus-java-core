package org.libprunus.core.plugin.aot.log;

import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import org.libprunus.core.log.runtime.LogLevel;

final class SyntheticExitEmitter {

    private SyntheticExitEmitter() {
        throw new UnsupportedOperationException();
    }

    static void emit(
            ClassVisitor cv,
            String classInternalName,
            AotMethodLoggingTransformer.SyntheticMethodRequest request,
            List<FieldExtractorRef> fieldExtractors) {
        MethodDescription method = request.method();
        AotMethodLoggingTransformer.MethodLogContext context = request.context();
        LogLevel exitLogLevel = context.exitLogLevel();
        if (exitLogLevel == LogLevel.OFF) {
            return;
        }

        String methodName =
                WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + method.getInternalName() + request.overloadSuffix();
        MethodExitReturnPlanConsumer.ExitReturnPlan exitReturn =
                MethodExitReturnPlanConsumer.consume(method, request.methodPlan());
        String descriptor = SyntheticMethodEmitter.buildSyntheticExitDescriptor(method, exitReturn.returnType());

        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, methodName, descriptor, null, null);
        mv.visitCode();

        int returnSlotCount = exitReturn.returnType() == Type.VOID_TYPE
                ? 0
                : exitReturn.returnType().getSize();
        int eventSlot = 1 + returnSlotCount;
        int contextSlot = eventSlot + 1;
        int exceptionSlot = contextSlot + 1;

        SyntheticMethodSkeleton.emitWithErrorBoundary(
                mv,
                classInternalName,
                method,
                exitLogLevel,
                eventSlot,
                contextSlot,
                exceptionSlot,
                fieldExtractors,
                (m, eSlot, cSlot) -> emitExitBody(m, cSlot, exitReturn.returnType(), exitReturn.masked(), context));

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitExitBody(
            MethodVisitor mv,
            int contextSlot,
            Type returnType,
            boolean returnMasked,
            AotMethodLoggingTransformer.MethodLogContext context) {
        String exitPrefix = "|< [EXIT] " + context.renderedClassName() + "." + context.renderedMethodName() + "(";
        if (returnType == Type.VOID_TYPE) {
            emitAcquireWithPrefix(mv, contextSlot, exitPrefix + ")");
        } else if (returnMasked) {
            emitAcquireWithPrefix(mv, contextSlot, exitPrefix + "value=" + WeavingInternalNames.MASK_SENTINEL + ")");
        } else {
            emitAcquireWithPrefix(mv, contextSlot, exitPrefix + "value=");
            int returnValueSlot = 1;
            emitReturnValue(mv, contextSlot, returnType, returnValueSlot);
            AsmEmitHelper.appendString(mv, contextSlot, ")");
        }
    }

    private static void emitReturnValue(MethodVisitor mv, int contextSlot, Type returnType, int returnValueSlot) {
        if (returnType.getSort() < Type.ARRAY) {
            SyntheticValueAppender.appendPrimitive(mv, contextSlot, returnType, returnValueSlot);
        } else {
            SyntheticValueAppender.appendRender(mv, contextSlot, returnValueSlot);
        }
    }

    private static void emitAcquireWithPrefix(MethodVisitor mv, int contextSlot, String prefix) {
        mv.visitLdcInsn(prefix);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME,
                "acquireWithPrefix",
                AsmDescriptors.ACQUIRE_WITH_PREFIX_DESCRIPTOR,
                false);
        mv.visitVarInsn(Opcodes.ASTORE, contextSlot);
    }
}
