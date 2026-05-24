package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.jar.asm.Type;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

final class AsmDescriptors {

    static final String LOGGER_INTERNAL_NAME = Type.getInternalName(Logger.class);
    static final String LOGGING_EVENT_BUILDER_INTERNAL_NAME = Type.getInternalName(LoggingEventBuilder.class);
    static final String STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME =
            "org/libprunus/core/log/runtime/StringBuilderWithContext";
    static final String STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR =
            "Lorg/libprunus/core/log/runtime/StringBuilderWithContext;";

    static final String STRING_BUILDER_ACQUIRE_DESCRIPTOR = "()" + STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR;
    static final String STRING_BUILDER_RELEASE_DESCRIPTOR = "(" + STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR + ")V";
    static final String STRING_BUILDER_TO_STRING_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(String.class));

    static final String LOGGER_IS_ENABLED_DESCRIPTOR = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
    static final String LOGGER_FLUENT_DESCRIPTOR =
            Type.getMethodDescriptor(Type.getObjectType(LOGGING_EVENT_BUILDER_INTERNAL_NAME));

    static final String RUNTIME_TO_STRING_FALLBACK_DESCRIPTOR = Type.getMethodDescriptor(
            Type.getType(String.class),
            Type.getType(String.class),
            Type.getObjectType(STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME),
            Type.getType(Throwable.class));
    static final String RUNTIME_HANDLE_RENDER_FAILURE_DESCRIPTOR = Type.getMethodDescriptor(
            Type.VOID_TYPE,
            Type.getType(String.class),
            Type.getObjectType(STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME),
            Type.getType(Throwable.class));
    static final String CONTEXT_IS_TRUNCATED_DESCRIPTOR = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
    static final String CONTEXT_SET_MAX_MESSAGE_LENGTH_DESCRIPTOR =
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE);
    static final String CONTEXT_APPEND_TEXT_DESCRIPTOR =
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(String.class));
    static final String CONTEXT_MARK_RENDER_TRUNCATION_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE);
    static final String CONTEXT_APPEND_OBJECT_DESCRIPTOR =
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class));
    static final String ACQUIRE_WITH_PREFIX_DESCRIPTOR = Type.getMethodDescriptor(
            Type.getObjectType(STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME), Type.getType(String.class));
    static final String CONTEXT_LOG_AND_RELEASE_DESCRIPTOR =
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.getObjectType(LOGGING_EVENT_BUILDER_INTERNAL_NAME));
    static final String ADD_KEY_VALUE_DESCRIPTOR = Type.getMethodDescriptor(
            Type.getObjectType(LOGGING_EVENT_BUILDER_INTERNAL_NAME),
            Type.getType(String.class),
            Type.getType(Object.class));
    static final String ENRICH_METHOD_DESCRIPTOR = Type.getMethodDescriptor(
            Type.getObjectType(LOGGING_EVENT_BUILDER_INTERNAL_NAME),
            Type.getObjectType(LOGGING_EVENT_BUILDER_INTERNAL_NAME));

    static final String RUNTIME_IS_ENABLED_DESCRIPTOR = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);

    private AsmDescriptors() {
        throw new UnsupportedOperationException();
    }

    static String contextAppendPrimitiveDescriptor(Type primitiveType) {
        return Type.getMethodDescriptor(Type.BOOLEAN_TYPE, normalizeStringBuilderAppendType(primitiveType));
    }

    private static Type normalizeStringBuilderAppendType(Type primitiveType) {
        return switch (primitiveType.getSort()) {
            case Type.BOOLEAN -> Type.BOOLEAN_TYPE;
            case Type.CHAR -> Type.CHAR_TYPE;
            case Type.LONG -> Type.LONG_TYPE;
            case Type.FLOAT -> Type.FLOAT_TYPE;
            case Type.DOUBLE -> Type.DOUBLE_TYPE;
            default -> Type.INT_TYPE;
        };
    }
}
