package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.Type;
import org.libprunus.core.log.runtime.LogLevel;

final class LightweightInjectionPlanConsumer {

    private LightweightInjectionPlanConsumer() {
        throw new UnsupportedOperationException();
    }

    static LightweightInjectionPlan consume(
            MethodDescription method,
            LogLevel enterLogLevel,
            LogLevel exitLogLevel,
            String overloadSuffix,
            boolean returnIgnored) {
        Type returnType = Type.getReturnType(method.getDescriptor());
        int firstLocal = computeFirstFreeSlot(method);

        SlotAllocator slotAllocator = new SlotAllocator(firstLocal);
        int returnValueSlot;
        if (returnType != Type.VOID_TYPE && exitLogLevel != LogLevel.OFF) {
            returnValueSlot = slotAllocator.allocate(returnType);
        } else {
            returnValueSlot = -1;
        }

        int loggerSlot;
        if (enterLogLevel != LogLevel.OFF || exitLogLevel != LogLevel.OFF) {
            loggerSlot = slotAllocator.allocate(Type.getObjectType(AsmDescriptors.LOGGER_INTERNAL_NAME));
        } else {
            loggerSlot = -1;
        }

        String methodName = method.getInternalName();
        Type exitReturnType = returnIgnored ? Type.VOID_TYPE : returnType;
        return new LightweightInjectionPlan(
                returnType,
                firstLocal,
                returnValueSlot,
                loggerSlot,
                slotAllocator.nextSlot() - firstLocal,
                exitLogLevel != LogLevel.OFF ? new Label() : null,
                WeavingInternalNames.SYNTHETIC_ENTER_PREFIX + methodName + overloadSuffix,
                SyntheticMethodEmitter.buildSyntheticEnterDescriptor(method),
                WeavingInternalNames.SYNTHETIC_EXIT_PREFIX + methodName + overloadSuffix,
                SyntheticMethodEmitter.buildSyntheticExitDescriptor(exitReturnType),
                returnIgnored);
    }

    static String isEnabledMethodForLevel(LogLevel level) {
        return switch (level) {
            case TRACE -> "isTraceEnabled";
            case DEBUG -> "isDebugEnabled";
            case INFO -> "isInfoEnabled";
            case WARN -> "isWarnEnabled";
            case ERROR -> "isErrorEnabled";
            case OFF -> throw new IllegalStateException("OFF level should be skipped before level check");
        };
    }

    static String fluentAtLevelMethod(LogLevel level) {
        return switch (level) {
            case TRACE -> "atTrace";
            case DEBUG -> "atDebug";
            case INFO -> "atInfo";
            case WARN -> "atWarn";
            case ERROR -> "atError";
            case OFF -> throw new IllegalStateException("OFF level should be skipped before fluent invocation");
        };
    }

    private static int computeFirstFreeSlot(MethodDescription method) {
        int slot = method.isStatic() ? 0 : 1;
        for (ParameterDescription param : method.getParameters()) {
            slot += Type.getType(param.getType().asErasure().getDescriptor()).getSize();
        }
        return slot;
    }

    record LightweightInjectionPlan(
            Type returnType,
            int firstLocal,
            int returnValueSlot,
            int loggerSlot,
            int shiftAmount,
            Label exitEpilogueLabel,
            String syntheticEnterName,
            String syntheticEnterDescriptor,
            String syntheticExitName,
            String syntheticExitDescriptor,
            boolean returnIgnored) {}

    private static final class SlotAllocator {
        private int currentSlot;

        private SlotAllocator(int startSlot) {
            this.currentSlot = startSlot;
        }

        private int allocate(Type type) {
            int allocated = currentSlot;
            currentSlot += type.getSize();
            return allocated;
        }

        private int nextSlot() {
            return currentSlot;
        }
    }
}
