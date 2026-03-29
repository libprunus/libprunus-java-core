package org.libprunus.core.plugin.aot.log;

import static net.bytebuddy.matcher.ElementMatchers.hasDescriptor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.ConstantDynamic;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.visitor.LocalVariableAwareMethodVisitor;
import org.libprunus.core.log.annotation.LogIgnore;
import org.libprunus.core.log.annotation.MaskStrategy;
import org.libprunus.core.log.annotation.Sensitive;
import org.libprunus.core.log.annotation.SensitiveReturn;

final class AotMethodLoggingTransformer extends AsmVisitorWrapper.AbstractBase {

    private static final int MAX_ARRAY_LOG_ELEMENTS = 100;
    private static final int MAX_ARRAY_LOG_DEPTH = 5;

    private static final String LOGGER_DESCRIPTOR = "Lorg/slf4j/Logger;";
    private static final Handle LOGGER_CONDY_BOOTSTRAP = new Handle(
            Opcodes.H_INVOKESTATIC,
            "org/libprunus/core/log/runtime/AotLogRuntime",
            "condyLoggerFactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Lorg/slf4j/Logger;",
            false);

    private final String classNameFormat;
    private final String enterLogLevel;
    private final String exitLogLevel;
    private final String exceptionLogLevel;
    private final boolean printExceptionStackTrace;

    AotMethodLoggingTransformer(
            String classNameFormat,
            boolean printExceptionStackTrace,
            String enterLogLevel,
            String exitLogLevel,
            String exceptionLogLevel) {
        this.classNameFormat = classNameFormat;
        this.printExceptionStackTrace = printExceptionStackTrace;
        this.enterLogLevel = enterLogLevel;
        this.exitLogLevel = exitLogLevel;
        this.exceptionLogLevel = exceptionLogLevel;
    }

    @Override
    public int mergeWriter(int flags) {
        return flags | ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
    }

    @Override
    public ClassVisitor wrap(
            TypeDescription instrumentedType,
            ClassVisitor classVisitor,
            Implementation.Context implementationContext,
            TypePool typePool,
            FieldList<InDefinedShape> fields,
            MethodList<?> methods,
            int writerFlags,
            int readerFlags) {
        return new ClassVisitor(Opcodes.ASM9, classVisitor) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("<init>".equals(name)
                        || "<clinit>".equals(name)
                        || (access & Opcodes.ACC_SYNTHETIC) != 0
                        || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    return delegate;
                }

                MethodDescription method = methods.filter(named(name).and(hasDescriptor(descriptor)))
                        .getOnly();
                if (method.isBridge()
                        || method.getDeclaredAnnotations().isAnnotationPresent(LogIgnore.class)
                        || ("toString".equals(name) && "()Ljava/lang/String;".equals(descriptor))) {
                    return delegate;
                }
                return new LoggingMethodVisitor(delegate, instrumentedType, method);
            }
        };
    }

    private final class LoggingMethodVisitor extends LocalVariableAwareMethodVisitor {

        private final MethodDescription method;
        private final String renderedClassName;
        private final String renderedMethodName;
        private final Type returnType;
        private final boolean sensitiveReturn;
        private final Label methodStart = new Label();
        private final Label methodEnd = new Label();
        private final Label normalExit = new Label();
        private final Label exceptionExit = new Label();
        private boolean hasNormalReturn;

        private LoggingMethodVisitor(
                MethodVisitor methodVisitor, TypeDescription declaringType, MethodDescription method) {
            super(methodVisitor, method);
            this.method = method;
            this.returnType = Type.getReturnType(method.getDescriptor());
            this.sensitiveReturn = method.getDeclaredAnnotations().isAnnotationPresent(SensitiveReturn.class);
            this.renderedClassName =
                    AotLogClassNameFormat.FULLY_QUALIFIED.name().equals(classNameFormat)
                            ? sanitizeForRecipe(declaringType.getName())
                            : sanitizeForRecipe(declaringType.getSimpleName());
            this.renderedMethodName = sanitizeForRecipe(method.getName());
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitLabel(methodStart);
            emitEnterLogging();
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                hasNormalReturn = true;
                mv.visitJumpInsn(Opcodes.GOTO, normalExit);
                return;
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mv.visitLabel(methodEnd);
            mv.visitTryCatchBlock(methodStart, methodEnd, exceptionExit, "java/lang/Throwable");
            int returnValueSlot = maxLocals;
            int throwableSlot = returnValueSlot + Math.max(returnType.getSize(), 1);

            if (hasNormalReturn) {
                mv.visitLabel(normalExit);
                emitNormalExitLogging(returnValueSlot);
            }

            mv.visitLabel(exceptionExit);
            emitExceptionExitLogging(throwableSlot);

            super.visitMaxs(maxStack, throwableSlot + 1);
        }

        private void emitEnterLogging() {
            Label skipLog = new Label();
            emitLevelGuard(enterLogLevel, skipLog);

            emitLoggerConstant();
            List<EnterParameter> parameters = collectEnterParameters();
            String message = enterMessageTemplate(parameters);
            List<EnterParameter> loggedValues = visibleEnterParameters(parameters);
            emitParameterizedLogCall(enterLogLevel, message, loggedValues);
            mv.visitLabel(skipLog);
        }

        private List<EnterParameter> collectEnterParameters() {
            List<EnterParameter> parameters = new ArrayList<>();
            int localSlot = firstFreeParameterSlot();
            for (ParameterDescription parameter : method.getParameters()) {
                Type parameterType =
                        Type.getType(parameter.getType().asErasure().getDescriptor());
                int slotSize = parameterType.getSize();
                if (parameter.getDeclaredAnnotations().isAnnotationPresent(LogIgnore.class)) {
                    localSlot += slotSize;
                    continue;
                }
                boolean sensitive =
                        isAllMaskStrategy(parameter.getDeclaredAnnotations().ofType(Sensitive.class));
                parameters.add(new EnterParameter(
                        sanitizeForRecipe(parameter.getName()), parameterType, localSlot, sensitive, slotSize));
                localSlot += slotSize;
            }
            return parameters;
        }

        private List<EnterParameter> visibleEnterParameters(List<EnterParameter> parameters) {
            List<EnterParameter> visible = new ArrayList<>();
            for (EnterParameter parameter : parameters) {
                if (!parameter.sensitive()) {
                    visible.add(parameter);
                }
            }
            return visible;
        }

        private String enterMessageTemplate(List<EnterParameter> parameters) {
            StringBuilder builder = new StringBuilder("|> [ENTER] ")
                    .append(renderedClassName)
                    .append('.')
                    .append(renderedMethodName)
                    .append('(');
            for (int index = 0; index < parameters.size(); index++) {
                EnterParameter parameter = parameters.get(index);
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(parameter.name()).append('=');
                if (parameter.sensitive()) {
                    builder.append("***");
                } else {
                    builder.append("{}");
                }
            }
            return builder.append(')').toString();
        }

        private void emitNormalExitLogging(int returnValueSlot) {
            if (returnType.getSort() == Type.VOID) {
                emitVoidExitLogging();
                mv.visitInsn(Opcodes.RETURN);
                return;
            }

            mv.visitVarInsn(returnType.getOpcode(Opcodes.ISTORE), returnValueSlot);

            Label skipLog = new Label();
            emitLevelGuard(exitLogLevel, skipLog);
            emitLoggerConstant();

            if (sensitiveReturn) {
                mv.visitLdcInsn("|< [EXIT] " + renderedClassName + "." + renderedMethodName + "(value=***)");
                invokeLogger(exitLogLevel, "(Ljava/lang/String;)V");
            } else {
                mv.visitLdcInsn("|< [EXIT] " + renderedClassName + "." + renderedMethodName + "(value={})");
                emitValueFromLocalForLogging(returnType, returnValueSlot);
                invokeLogger(exitLogLevel, "(Ljava/lang/String;Ljava/lang/Object;)V");
            }

            mv.visitLabel(skipLog);
            mv.visitVarInsn(returnType.getOpcode(Opcodes.ILOAD), returnValueSlot);
            mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
        }

        private void emitExceptionExitLogging(int throwableSlot) {
            mv.visitVarInsn(Opcodes.ASTORE, throwableSlot);
            mv.visitVarInsn(Opcodes.ALOAD, throwableSlot);
            emitLoggerConstant();
            mv.visitLdcInsn(exceptionLogLevel);
            mv.visitLdcInsn("|< [EXIT - EXCEPTION] " + renderedClassName + "." + renderedMethodName);
            mv.visitInsn(printExceptionStackTrace ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/libprunus/core/log/runtime/AotLogRuntime",
                    "logException",
                    "(Ljava/lang/Throwable;Lorg/slf4j/Logger;Ljava/lang/String;Ljava/lang/String;Z)V",
                    false);
            mv.visitVarInsn(Opcodes.ALOAD, throwableSlot);
            mv.visitInsn(Opcodes.ATHROW);
        }

        private void emitVoidExitLogging() {
            Label skipLog = new Label();
            emitLevelGuard(exitLogLevel, skipLog);

            emitLoggerConstant();
            mv.visitLdcInsn("|< [EXIT] " + renderedClassName + "." + renderedMethodName + "()");
            invokeLogger(exitLogLevel, "(Ljava/lang/String;)V");
            mv.visitLabel(skipLog);
        }

        private void emitParameterizedLogCall(String level, String message, List<EnterParameter> values) {
            int valueCount = values.size();
            mv.visitLdcInsn(message);
            if (valueCount == 0) {
                invokeLogger(level, "(Ljava/lang/String;)V");
                return;
            }
            if (valueCount == 1) {
                emitValueFromLocalForLogging(values.get(0).type(), values.get(0).localSlot());
                invokeLogger(level, "(Ljava/lang/String;Ljava/lang/Object;)V");
                return;
            }
            if (valueCount == 2) {
                emitValueFromLocalForLogging(values.get(0).type(), values.get(0).localSlot());
                emitValueFromLocalForLogging(values.get(1).type(), values.get(1).localSlot());
                invokeLogger(level, "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V");
                return;
            }

            pushInt(valueCount);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            for (int index = 0; index < valueCount; index++) {
                EnterParameter parameter = values.get(index);
                mv.visitInsn(Opcodes.DUP);
                pushInt(index);
                emitValueFromLocalForLogging(parameter.type(), parameter.localSlot());
                mv.visitInsn(Opcodes.AASTORE);
            }
            invokeLogger(level, "(Ljava/lang/String;[Ljava/lang/Object;)V");
        }

        private void emitValueFromLocalForLogging(Type valueType, int localSlot) {
            mv.visitVarInsn(valueType.getOpcode(Opcodes.ILOAD), localSlot);
            if (valueType.getSort() == Type.ARRAY) {
                emitTypedArrayToString(valueType);
                return;
            }
            boxPrimitiveIfNeeded(valueType);
        }

        private void emitTypedArrayToString(Type valueType) {
            String descriptor = valueType.getDescriptor();
            if (isPrimitiveArrayDescriptor(descriptor)) {
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitLdcInsn(MAX_ARRAY_LOG_ELEMENTS);
                mv.visitLdcInsn(MAX_ARRAY_LOG_DEPTH);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/libprunus/core/log/runtime/AotLogRuntime",
                        "safeArrayToString",
                        "(" + descriptor + "III)Ljava/lang/String;",
                        false);
                return;
            }
            mv.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/Object;");
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitLdcInsn(MAX_ARRAY_LOG_ELEMENTS);
            mv.visitLdcInsn(MAX_ARRAY_LOG_DEPTH);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/libprunus/core/log/runtime/AotLogRuntime",
                    "safeArrayToString",
                    "([Ljava/lang/Object;III)Ljava/lang/String;",
                    false);
        }

        private boolean isPrimitiveArrayDescriptor(String descriptor) {
            return descriptor.length() == 2
                    && descriptor.charAt(0) == '['
                    && "ZBCSIJFD".indexOf(descriptor.charAt(1)) >= 0;
        }

        private void boxPrimitiveIfNeeded(Type type) {
            switch (type.getSort()) {
                case Type.BOOLEAN ->
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                case Type.CHAR ->
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                case Type.BYTE ->
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                case Type.SHORT ->
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                case Type.INT ->
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                case Type.FLOAT ->
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                case Type.LONG ->
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                case Type.DOUBLE ->
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                default -> {}
            }
        }

        private void pushInt(int value) {
            if (value >= -1 && value <= 5) {
                mv.visitInsn(value == -1 ? Opcodes.ICONST_M1 : Opcodes.ICONST_0 + value);
                return;
            }
            if (value <= Byte.MAX_VALUE) {
                mv.visitIntInsn(Opcodes.BIPUSH, value);
                return;
            }
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        }

        private void emitLoggerConstant() {
            mv.visitLdcInsn(new ConstantDynamic("LIBPRUNUS_AOT_LOGGER", LOGGER_DESCRIPTOR, LOGGER_CONDY_BOOTSTRAP));
        }

        private int firstFreeParameterSlot() {
            return method.isStatic() ? 0 : 1;
        }

        private void emitLevelGuard(String level, Label skipLog) {
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC, "org/libprunus/core/log/runtime/AotLogRuntime", "isEnabled", "()Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, skipLog);
            emitLoggerConstant();
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", enabledMethodName(level), "()Z", true);
            mv.visitJumpInsn(Opcodes.IFEQ, skipLog);
        }

        private String enabledMethodName(String level) {
            return switch (level) {
                case "TRACE" -> "isTraceEnabled";
                case "DEBUG" -> "isDebugEnabled";
                case "INFO" -> "isInfoEnabled";
                case "WARN" -> "isWarnEnabled";
                case "ERROR" -> "isErrorEnabled";
                default -> throw new IllegalArgumentException("Unsupported level: " + level);
            };
        }

        private void invokeLogger(String level, String descriptor) {
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", level.toLowerCase(), descriptor, true);
        }

        private record EnterParameter(String name, Type type, int localSlot, boolean sensitive, int slotSize) {}
    }

    private static String sanitizeForRecipe(String text) {
        int firstBad = -1;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\u0001' || value == '\u0002' || Character.isISOControl(value)) {
                firstBad = index;
                break;
            }
        }
        if (firstBad == -1) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        builder.append(text, 0, firstBad);
        for (int index = firstBad; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\u0001' || value == '\u0002' || Character.isISOControl(value)) {
                builder.append('?');
            } else {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private static boolean isAllMaskStrategy(AnnotationDescription annotation) {
        if (annotation == null) {
            return false;
        }
        EnumerationDescription strategy = annotation.getValue("strategy").resolve(EnumerationDescription.class);
        return MaskStrategy.ALL.name().equals(strategy.getValue());
    }
}
