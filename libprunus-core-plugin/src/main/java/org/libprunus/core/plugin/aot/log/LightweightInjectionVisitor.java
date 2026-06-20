package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ConstantDynamic;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.jar.asm.TypePath;
import net.bytebuddy.utility.visitor.LocalVariableAwareMethodVisitor;
import org.jspecify.annotations.Nullable;
import org.libprunus.core.log.runtime.LogLevel;

final class LightweightInjectionVisitor extends LocalVariableAwareMethodVisitor {

    private static final Handle LOGGER_CONDY_BOOTSTRAP = new Handle(
            Opcodes.H_INVOKESTATIC,
            WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME,
            "condyLoggerFactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;)Lorg/slf4j/Logger;",
            false);

    private final MethodDescription method;
    private final String classInternalName;
    private final LogLevel enterLogLevel;
    private final LogLevel exitLogLevel;
    private final Type returnType;
    private final int firstLocal;
    private final int returnValueSlot;
    private final int loggerSlot;
    private final int shiftAmount;
    private final @Nullable Label exitEpilogueLabel;
    private final String syntheticEnterName;
    private final String syntheticEnterDescriptor;
    private final String syntheticExitName;
    private final String syntheticExitDescriptor;
    private final boolean returnIgnored;
    private boolean hasReturn;

    LightweightInjectionVisitor(
            MethodVisitor methodVisitor,
            MethodDescription method,
            String classInternalName,
            LogLevel enterLogLevel,
            LogLevel exitLogLevel,
            String overloadSuffix,
            boolean returnIgnored) {
        super(methodVisitor, method);
        this.method = method;
        this.classInternalName = classInternalName;
        this.enterLogLevel = enterLogLevel;
        this.exitLogLevel = exitLogLevel;
        LightweightInjectionPlanConsumer.LightweightInjectionPlan plan = LightweightInjectionPlanConsumer.consume(
                method, enterLogLevel, exitLogLevel, overloadSuffix, returnIgnored);
        this.returnType = plan.returnType();
        this.firstLocal = plan.firstLocal();
        this.returnValueSlot = plan.returnValueSlot();
        this.loggerSlot = plan.loggerSlot();
        this.shiftAmount = plan.shiftAmount();
        this.exitEpilogueLabel = plan.exitEpilogueLabel();
        this.syntheticEnterName = plan.syntheticEnterName();
        this.syntheticExitName = plan.syntheticExitName();
        this.syntheticEnterDescriptor = plan.syntheticEnterDescriptor();
        this.syntheticExitDescriptor = plan.syntheticExitDescriptor();
        this.returnIgnored = plan.returnIgnored();
    }

    @Override
    public void visitCode() {
        super.visitCode();
        emitEnterGuard();
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        super.visitVarInsn(opcode, var >= firstLocal && shiftAmount > 0 ? var + shiftAmount : var);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        super.visitIincInsn(var >= firstLocal && shiftAmount > 0 ? var + shiftAmount : var, increment);
    }

    @Override
    public void visitLocalVariable(
            String name, String descriptor, String signature, Label start, Label end, int index) {
        super.visitLocalVariable(
                name,
                descriptor,
                signature,
                start,
                end,
                index >= firstLocal && shiftAmount > 0 ? index + shiftAmount : index);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(
            int typeRef,
            TypePath typePath,
            Label[] start,
            Label[] end,
            int[] index,
            String descriptor,
            boolean visible) {
        if (shiftAmount <= 0) {
            return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
        }
        int[] shifted = new int[index.length];
        for (int i = 0; i < index.length; i++) {
            shifted[i] = index[i] >= firstLocal ? index[i] + shiftAmount : index[i];
        }
        return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, shifted, descriptor, visible);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.RETURN) {
            hasReturn = true;
            if (exitLogLevel == LogLevel.OFF) {
                super.visitInsn(opcode);
                return;
            }
            mv.visitJumpInsn(Opcodes.GOTO, exitEpilogueLabel);
            return;
        }
        if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.ARETURN) {
            hasReturn = true;
            if (exitLogLevel == LogLevel.OFF) {
                super.visitInsn(opcode);
                return;
            }
            mv.visitVarInsn(returnType.getOpcode(Opcodes.ISTORE), returnValueSlot);
            mv.visitJumpInsn(Opcodes.GOTO, exitEpilogueLabel);
            return;
        }
        super.visitInsn(opcode);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        if (exitLogLevel != LogLevel.OFF && hasReturn) {
            emitExitEpilogue();
        }
        super.visitMaxs(maxStack, maxLocals);
    }

    private void emitEnterGuard() {
        if (enterLogLevel == LogLevel.OFF) {
            return;
        }
        emitLoggerGuardAndInvoke(
                enterLogLevel, ignored -> pushOriginalParameters(), syntheticEnterName, syntheticEnterDescriptor);
    }

    private void emitExitEpilogue() {
        if (exitLogLevel == LogLevel.OFF) {
            return;
        }
        mv.visitLabel(exitEpilogueLabel);
        emitLoggerGuardAndInvoke(
                exitLogLevel,
                methodVisitor -> {
                    if (returnType != Type.VOID_TYPE && !returnIgnored) {
                        methodVisitor.visitVarInsn(returnType.getOpcode(Opcodes.ILOAD), returnValueSlot);
                    }
                },
                syntheticExitName,
                syntheticExitDescriptor);
        if (returnType == Type.VOID_TYPE) {
            super.visitInsn(Opcodes.RETURN);
        } else {
            mv.visitVarInsn(returnType.getOpcode(Opcodes.ILOAD), returnValueSlot);
            super.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
        }
    }

    private void emitLoggerGuardAndInvoke(
            LogLevel level,
            java.util.function.Consumer<MethodVisitor> emitArgs,
            String syntheticName,
            String syntheticDescriptor) {
        Label skip = new Label();
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME,
                "isEnabled",
                AsmDescriptors.RUNTIME_IS_ENABLED_DESCRIPTOR,
                false);
        mv.visitJumpInsn(Opcodes.IFEQ, skip);
        emitLoggerConstant();
        mv.visitVarInsn(Opcodes.ASTORE, loggerSlot);
        mv.visitVarInsn(Opcodes.ALOAD, loggerSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                AsmDescriptors.LOGGER_INTERNAL_NAME,
                LightweightInjectionPlanConsumer.isEnabledMethodForLevel(level),
                AsmDescriptors.LOGGER_IS_ENABLED_DESCRIPTOR,
                true);
        mv.visitJumpInsn(Opcodes.IFEQ, skip);
        mv.visitVarInsn(Opcodes.ALOAD, loggerSlot);
        emitArgs.accept(mv);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, syntheticName, syntheticDescriptor, false);
        mv.visitLabel(skip);
    }

    private void pushOriginalParameters() {
        int slot = method.isStatic() ? 0 : 1;
        for (ParameterDescription param : method.getParameters()) {
            Type paramType = Type.getType(param.getType().asErasure().getDescriptor());
            mv.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), slot);
            slot += paramType.getSize();
        }
    }

    private void emitLoggerConstant() {
        mv.visitLdcInsn(new ConstantDynamic(
                "AOT_LOGGER",
                Type.getObjectType(AsmDescriptors.LOGGER_INTERNAL_NAME).getDescriptor(),
                LOGGER_CONDY_BOOTSTRAP,
                classInternalName.replace('/', '.')));
    }
}
