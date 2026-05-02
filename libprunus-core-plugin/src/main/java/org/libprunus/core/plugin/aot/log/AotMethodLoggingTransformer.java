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
import org.libprunus.core.log.annotation.AotNoReplace;
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
    private final AotLogLevel enterLogLevel;
    private final AotLogLevel exitLogLevel;

    AotMethodLoggingTransformer(String classNameFormat, AotLogLevel enterLogLevel, AotLogLevel exitLogLevel) {
        this.classNameFormat = classNameFormat;
        this.enterLogLevel = enterLogLevel;
        this.exitLogLevel = exitLogLevel;
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
                        || method.getDeclaredAnnotations().isAnnotationPresent(AotNoReplace.class)
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
            emitEnterLogging();
        }

        @Override
        public void visitInsn(int opcode) {
            // TODO: Evaluate whether a shared exit node is worth the verifier and local-slot complexity across this
            // method shape range, versus keeping duplicated inline exit logging and simpler control flow.
            if (opcode == Opcodes.RETURN) {
                emitVoidExitLogging();
                super.visitInsn(opcode);
                return;
            }
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.ARETURN) {
                int returnValueSlot = getFreeOffset();
                mv.visitVarInsn(returnType.getOpcode(Opcodes.ISTORE), returnValueSlot);
                emitInlineNormalExitLogging(returnType, returnValueSlot);
                mv.visitVarInsn(returnType.getOpcode(Opcodes.ILOAD), returnValueSlot);
                super.visitInsn(opcode);
                return;
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack, maxLocals);
        }

        private void emitEnterLogging() {
            Label done = new Label();
            Label logStart = new Label();
            Label logEnd = new Label();
            Label logFailure = new Label();

            mv.visitLabel(logStart);
            emitLevelGuard(enterLogLevel, done);

            emitLoggerConstant();
            List<EnterParameter> parameters = collectEnterParameters();
            String message = enterMessageTemplate(parameters);
            List<EnterParameter> loggedValues = visibleEnterParameters(parameters);
            emitParameterizedLogCall(enterLogLevel, message, loggedValues);
            mv.visitLabel(logEnd);
            mv.visitJumpInsn(Opcodes.GOTO, done);

            mv.visitLabel(logFailure);
            emitLogFailurePolicy();
            mv.visitLabel(done);
            mv.visitTryCatchBlock(logStart, logEnd, logFailure, "java/lang/Throwable");
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

        private void emitInlineNormalExitLogging(Type valueType, int returnValueSlot) {
            Label done = new Label();
            Label logStart = new Label();
            Label logEnd = new Label();
            Label logFailure = new Label();

            mv.visitLabel(logStart);
            emitLevelGuard(exitLogLevel, done);
            emitLoggerConstant();

            if (sensitiveReturn) {
                mv.visitLdcInsn("|< [EXIT] " + renderedClassName + "." + renderedMethodName + "(value=***)");
                invokeLogger(exitLogLevel, "(Ljava/lang/String;)V");
            } else {
                mv.visitLdcInsn("|< [EXIT] " + renderedClassName + "." + renderedMethodName + "(value={})");
                emitValueFromLocalForLogging(valueType, returnValueSlot);
                invokeLogger(exitLogLevel, "(Ljava/lang/String;Ljava/lang/Object;)V");
            }
            mv.visitLabel(logEnd);
            mv.visitJumpInsn(Opcodes.GOTO, done);

            mv.visitLabel(logFailure);
            emitLogFailurePolicy();
            mv.visitLabel(done);
            mv.visitTryCatchBlock(logStart, logEnd, logFailure, "java/lang/Throwable");
        }

        private void emitVoidExitLogging() {
            Label done = new Label();
            Label logStart = new Label();
            Label logEnd = new Label();
            Label logFailure = new Label();

            mv.visitLabel(logStart);
            emitLevelGuard(exitLogLevel, done);

            emitLoggerConstant();
            mv.visitLdcInsn("|< [EXIT] " + renderedClassName + "." + renderedMethodName + "()");
            invokeLogger(exitLogLevel, "(Ljava/lang/String;)V");
            mv.visitLabel(logEnd);
            mv.visitJumpInsn(Opcodes.GOTO, done);

            mv.visitLabel(logFailure);
            emitLogFailurePolicy();
            mv.visitLabel(done);
            mv.visitTryCatchBlock(logStart, logEnd, logFailure, "java/lang/Throwable");
        }

        private void emitLogFailurePolicy() {
            Label nonError = new Label();
            mv.visitInsn(Opcodes.DUP);
            mv.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/Error");
            mv.visitJumpInsn(Opcodes.IFEQ, nonError);
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Error");
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitLabel(nonError);
            // TODO: Define the non-Error failure policy for logging side effects in a way that preserves
            // business-path resilience while still making loss of logging observability measurable.
            mv.visitInsn(Opcodes.POP);
        }

        private void emitParameterizedLogCall(AotLogLevel level, String message, List<EnterParameter> values) {
            int valueCount = values.size();
            if (valueCount == 0) {
                mv.visitLdcInsn(message);
                invokeLogger(level, "(Ljava/lang/String;)V");
                return;
            }
            if (valueCount == 1) {
                mv.visitLdcInsn(message);
                emitValueFromLocalForLogging(values.get(0).type(), values.get(0).localSlot());
                invokeLogger(level, "(Ljava/lang/String;Ljava/lang/Object;)V");
                return;
            }
            if (valueCount == 2) {
                mv.visitLdcInsn(message);
                emitValueFromLocalForLogging(values.get(0).type(), values.get(0).localSlot());
                emitValueFromLocalForLogging(values.get(1).type(), values.get(1).localSlot());
                invokeLogger(level, "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V");
                return;
            }

            mv.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    "org/slf4j/Logger",
                    fluentAtLevelMethod(level),
                    "()Lorg/slf4j/spi/LoggingEventBuilder;",
                    true);

            for (EnterParameter parameter : values) {
                emitValueFromLocalForLogging(parameter.type(), parameter.localSlot());
                mv.visitMethodInsn(
                        Opcodes.INVOKEINTERFACE,
                        "org/slf4j/spi/LoggingEventBuilder",
                        "addArgument",
                        "(Ljava/lang/Object;)Lorg/slf4j/spi/LoggingEventBuilder;",
                        true);
            }

            mv.visitLdcInsn(message);
            mv.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE, "org/slf4j/spi/LoggingEventBuilder", "log", "(Ljava/lang/String;)V", true);
        }

        private void emitValueFromLocalForLogging(Type valueType, int localSlot) {
            mv.visitVarInsn(valueType.getOpcode(Opcodes.ILOAD), localSlot);
            if (valueType.getSort() < Type.ARRAY) {
                boxPrimitiveIfNeeded(valueType);
                return;
            }
            // TODO: Evaluate eager safe stringification against deferred rendering adapters, balancing deterministic
            // safety guarantees with allocation cost and logger-backend execution paths after level checks.
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitLdcInsn(MAX_ARRAY_LOG_ELEMENTS);
            mv.visitLdcInsn(MAX_ARRAY_LOG_DEPTH);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/libprunus/core/log/runtime/AotLogRuntime",
                    "safeObjectToString",
                    "(Ljava/lang/Object;III)Ljava/lang/String;",
                    false);
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

        private void emitLoggerConstant() {
            mv.visitLdcInsn(new ConstantDynamic("LIBPRUNUS_AOT_LOGGER", LOGGER_DESCRIPTOR, LOGGER_CONDY_BOOTSTRAP));
        }

        private int firstFreeParameterSlot() {
            return method.isStatic() ? 0 : 1;
        }

        private void emitLevelGuard(AotLogLevel level, Label skipLog) {
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC, "org/libprunus/core/log/runtime/AotLogRuntime", "isEnabled", "()Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, skipLog);
            emitLoggerConstant();
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", enabledMethodName(level), "()Z", true);
            mv.visitJumpInsn(Opcodes.IFEQ, skipLog);
        }

        private String enabledMethodName(AotLogLevel level) {
            return switch (level) {
                case TRACE -> "isTraceEnabled";
                case DEBUG -> "isDebugEnabled";
                case INFO -> "isInfoEnabled";
                case WARN -> "isWarnEnabled";
                case ERROR -> "isErrorEnabled";
            };
        }

        private void invokeLogger(AotLogLevel level, String descriptor) {
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", loggerMethodName(level), descriptor, true);
        }

        private String loggerMethodName(AotLogLevel level) {
            return switch (level) {
                case TRACE -> "trace";
                case DEBUG -> "debug";
                case INFO -> "info";
                case WARN -> "warn";
                case ERROR -> "error";
            };
        }

        private String fluentAtLevelMethod(AotLogLevel level) {
            return switch (level) {
                case TRACE -> "atTrace";
                case DEBUG -> "atDebug";
                case INFO -> "atInfo";
                case WARN -> "atWarn";
                case ERROR -> "atError";
            };
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
