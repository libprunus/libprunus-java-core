package org.libprunus.core.plugin.aot.log;

import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import org.libprunus.core.log.runtime.LogLevel;

final class SyntheticEnterEmitter {

    private SyntheticEnterEmitter() {
        throw new UnsupportedOperationException();
    }

    static void emit(
            ClassVisitor cv,
            String classInternalName,
            AotMethodLoggingTransformer.SyntheticMethodRequest request,
            List<FieldExtractorRef> fieldExtractors) {
        MethodDescription method = request.method();
        AotMethodLoggingTransformer.MethodLogContext context = request.context();
        LogLevel enterLogLevel = context.enterLogLevel();
        if (enterLogLevel == LogLevel.OFF) {
            return;
        }

        String methodName =
                WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + method.getInternalName() + request.overloadSuffix();
        String descriptor = SyntheticMethodEmitter.buildSyntheticEnterDescriptor(method);

        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, methodName, descriptor, null, null);
        mv.visitCode();

        int paramSlotCount = computeParameterSlotCount(method);
        int eventSlot = 1 + paramSlotCount;
        int contextSlot = eventSlot + 1;
        int exceptionSlot = contextSlot + 1;

        List<MethodEnterParameterPlanConsumer.EnterParamPlan> enterParams =
                MethodEnterParameterPlanConsumer.consume(method, request.methodPlan());

        SyntheticMethodSkeleton.emitWithErrorBoundary(
                mv,
                classInternalName,
                method,
                enterLogLevel,
                eventSlot,
                contextSlot,
                exceptionSlot,
                fieldExtractors,
                (m, eSlot, cSlot) -> emitEnterBody(m, cSlot, enterParams, context));

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitEnterBody(
            MethodVisitor mv,
            int contextSlot,
            List<MethodEnterParameterPlanConsumer.EnterParamPlan> params,
            AotMethodLoggingTransformer.MethodLogContext context) {
        String basePrefix = "|> [ENTER] " + context.renderedClassName() + "." + context.renderedMethodName() + "(";
        StringBuilder staticAccumulator = new StringBuilder(
                params.isEmpty() ? basePrefix : basePrefix + params.get(0).name() + "=");
        boolean acquired = false;
        for (int i = 0; i < params.size(); i++) {
            MethodEnterParameterPlanConsumer.EnterParamPlan p = params.get(i);
            if (i > 0) {
                staticAccumulator.append(", ").append(p.name()).append("=");
            }
            if (p.masked()) {
                staticAccumulator.append(WeavingInternalNames.MASK_SENTINEL);
            } else {
                if (!acquired) {
                    emitAcquireWithPrefix(mv, contextSlot, staticAccumulator.toString());
                    acquired = true;
                } else {
                    AsmEmitHelper.appendString(mv, contextSlot, staticAccumulator.toString());
                }
                staticAccumulator.setLength(0);
                emitParameterValue(mv, contextSlot, p);
            }
        }
        staticAccumulator.append(")");
        if (!acquired) {
            emitAcquireWithPrefix(mv, contextSlot, staticAccumulator.toString());
        } else {
            AsmEmitHelper.appendString(mv, contextSlot, staticAccumulator.toString());
        }
    }

    private static void emitParameterValue(
            MethodVisitor mv, int contextSlot, MethodEnterParameterPlanConsumer.EnterParamPlan p) {
        if (p.type().getSort() < Type.ARRAY) {
            SyntheticValueAppender.appendPrimitive(mv, contextSlot, p.type(), p.syntheticSlot());
        } else {
            SyntheticValueAppender.appendRender(mv, contextSlot, p.syntheticSlot());
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

    private static int computeParameterSlotCount(MethodDescription method) {
        int count = 0;
        for (ParameterDescription param : method.getParameters()) {
            count += Type.getType(param.getType().asErasure().getDescriptor()).getSize();
        }
        return count;
    }
}
