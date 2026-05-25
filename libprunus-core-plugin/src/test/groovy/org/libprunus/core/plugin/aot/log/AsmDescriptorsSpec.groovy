package org.libprunus.core.plugin.aot.log

import net.bytebuddy.jar.asm.Type
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.StringBuilderPool
import org.libprunus.core.log.runtime.StringBuilderWithContext
import org.slf4j.Logger
import org.slf4j.spi.LoggingEventBuilder
import spock.lang.Specification

class AsmDescriptorsSpec extends Specification {

    def "LOGGER_INTERNAL_NAME equals SLF4J Logger binary name"() {
        expect:
        AsmDescriptors.LOGGER_INTERNAL_NAME == Type.getInternalName(Logger)
        AsmDescriptors.LOGGER_INTERNAL_NAME == "org/slf4j/Logger"
    }

    def "LOGGING_EVENT_BUILDER_INTERNAL_NAME equals SLF4J LoggingEventBuilder binary name"() {
        expect:
        AsmDescriptors.LOGGING_EVENT_BUILDER_INTERNAL_NAME == Type.getInternalName(LoggingEventBuilder)
        AsmDescriptors.LOGGING_EVENT_BUILDER_INTERNAL_NAME == "org/slf4j/spi/LoggingEventBuilder"
    }

    def "STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME equals StringBuilderWithContext runtime binary name"() {
        expect:
        AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME == StringBuilderWithContext.name.replace('.', '/')
        AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME == "org/libprunus/core/log/runtime/StringBuilderWithContext"
    }

    def "STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR wraps internal name with L and semicolon"() {
        expect:
        AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR ==
                "L" + AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_INTERNAL_NAME + ";"
        AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR ==
                "Lorg/libprunus/core/log/runtime/StringBuilderWithContext;"
    }

    def "STRING_BUILDER_ACQUIRE_DESCRIPTOR matches StringBuilderPool acquire signature"() {
        given:
        def method = StringBuilderPool.getDeclaredMethod("acquire")

        expect:
        AsmDescriptors.STRING_BUILDER_ACQUIRE_DESCRIPTOR == Type.getMethodDescriptor(method)
        AsmDescriptors.STRING_BUILDER_ACQUIRE_DESCRIPTOR ==
                "()" + AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR
    }

    def "STRING_BUILDER_RELEASE_DESCRIPTOR matches StringBuilderPool release signature"() {
        given:
        def method = StringBuilderPool.getDeclaredMethod("release", StringBuilderWithContext)

        expect:
        AsmDescriptors.STRING_BUILDER_RELEASE_DESCRIPTOR == Type.getMethodDescriptor(method)
        AsmDescriptors.STRING_BUILDER_RELEASE_DESCRIPTOR ==
                "(" + AsmDescriptors.STRING_BUILDER_WITH_CONTEXT_DESCRIPTOR + ")V"
    }

    def "STRING_BUILDER_TO_STRING_DESCRIPTOR matches StringBuilderWithContext toString signature"() {
        given:
        def method = StringBuilderWithContext.getDeclaredMethod("toString")

        expect:
        AsmDescriptors.STRING_BUILDER_TO_STRING_DESCRIPTOR == Type.getMethodDescriptor(method)
        AsmDescriptors.STRING_BUILDER_TO_STRING_DESCRIPTOR == "()Ljava/lang/String;"
    }

    def "LOGGER_IS_ENABLED_DESCRIPTOR matches SLF4J Logger isXxxEnabled signature shape"() {
        given:
        def method = Logger.getDeclaredMethod("isInfoEnabled")

        expect:
        AsmDescriptors.LOGGER_IS_ENABLED_DESCRIPTOR == Type.getMethodDescriptor(method)
        AsmDescriptors.LOGGER_IS_ENABLED_DESCRIPTOR == "()Z"
    }

    def "LOGGER_FLUENT_DESCRIPTOR matches SLF4J Logger atXxx fluent entrypoint signature shape"() {
        given:
        def method = Logger.getDeclaredMethod("atInfo")

        expect:
        AsmDescriptors.LOGGER_FLUENT_DESCRIPTOR == Type.getMethodDescriptor(method)
        AsmDescriptors.LOGGER_FLUENT_DESCRIPTOR ==
                "()L" + AsmDescriptors.LOGGING_EVENT_BUILDER_INTERNAL_NAME + ";"
    }

    def "RUNTIME_TO_STRING_FALLBACK_DESCRIPTOR matches StringBuilderWithContext#recoverToStringFallback signature"() {
        given:
        def method = StringBuilderWithContext.getDeclaredMethod(
                "recoverToStringFallback", String, StringBuilderWithContext, Throwable)

        expect:
        AsmDescriptors.RUNTIME_TO_STRING_FALLBACK_DESCRIPTOR == Type.getMethodDescriptor(method)
    }

    def "RUNTIME_HANDLE_RENDER_FAILURE_DESCRIPTOR matches StringBuilderWithContext#handleRenderFailure signature"() {
        given:
        def method = StringBuilderWithContext.getDeclaredMethod(
                "handleRenderFailure", String, StringBuilderWithContext, Throwable)

        expect:
        AsmDescriptors.RUNTIME_HANDLE_RENDER_FAILURE_DESCRIPTOR == Type.getMethodDescriptor(method)
    }

    def "context instance method descriptors stay in sync with StringBuilderWithContext signatures"() {
        given:
        def method = StringBuilderWithContext.getDeclaredMethod(methodName, paramTypes as Class[])

        expect:
        AsmDescriptors[fieldName] == Type.getMethodDescriptor(method)

        where:
        fieldName                                       | methodName              | paramTypes
        "CONTEXT_IS_TRUNCATED_DESCRIPTOR"               | "isTruncated"           | []
        "CONTEXT_SET_MAX_MESSAGE_LENGTH_DESCRIPTOR"     | "setMaxMessageLength"   | [int]
        "CONTEXT_APPEND_TEXT_DESCRIPTOR"                | "append"                | [String]
        "CONTEXT_MARK_RENDER_TRUNCATION_DESCRIPTOR"     | "markRenderTruncation"  | []
        "CONTEXT_APPEND_OBJECT_DESCRIPTOR"              | "render"                | [Object]
    }

    def "ACQUIRE_WITH_PREFIX_DESCRIPTOR matches StringBuilderPool#acquireWithPrefix signature"() {
        given:
        def method = StringBuilderPool.getDeclaredMethod("acquireWithPrefix", String)

        expect:
        AsmDescriptors.ACQUIRE_WITH_PREFIX_DESCRIPTOR == Type.getMethodDescriptor(method)
    }

    def "CONTEXT_LOG_AND_RELEASE_DESCRIPTOR matches StringBuilderWithContext#logAndRelease signature"() {
        given:
        def method = StringBuilderWithContext.getDeclaredMethod("logAndRelease", LoggingEventBuilder)

        expect:
        AsmDescriptors.CONTEXT_LOG_AND_RELEASE_DESCRIPTOR == Type.getMethodDescriptor(method)
    }

    def "ADD_KEY_VALUE_DESCRIPTOR matches LoggingEventBuilder#addKeyValue signature"() {
        given:
        def method = LoggingEventBuilder.getDeclaredMethod("addKeyValue", String, Object)

        expect:
        AsmDescriptors.ADD_KEY_VALUE_DESCRIPTOR == Type.getMethodDescriptor(method)
    }

    def "ENRICH_METHOD_DESCRIPTOR is the shape (LoggingEventBuilder)LoggingEventBuilder"() {
        given:
        def lebType = Type.getObjectType(AsmDescriptors.LOGGING_EVENT_BUILDER_INTERNAL_NAME)
        def lebDesc = lebType.getDescriptor()

        expect:
        AsmDescriptors.ENRICH_METHOD_DESCRIPTOR == Type.getMethodDescriptor(lebType, lebType)
        AsmDescriptors.ENRICH_METHOD_DESCRIPTOR == "(" + lebDesc + ")" + lebDesc
    }

    def "RUNTIME_IS_ENABLED_DESCRIPTOR equals descriptor derived from LogRuntime#isEnabled signature"() {
        given:
        def expected = Type.getMethodDescriptor(LogRuntime.getDeclaredMethod("isEnabled"))

        expect:
        AsmDescriptors.RUNTIME_IS_ENABLED_DESCRIPTOR == expected
    }

    def "contextAppendPrimitiveDescriptor maps each ASM sort to the StringBuilderWithContext primitive append overload"() {
        expect:
        AsmDescriptors.contextAppendPrimitiveDescriptor(input) == expectedDescriptor

        where:
        input             || expectedDescriptor
        Type.BOOLEAN_TYPE || "(Z)Z"
        Type.CHAR_TYPE    || "(C)Z"
        Type.LONG_TYPE    || "(J)Z"
        Type.FLOAT_TYPE   || "(F)Z"
        Type.DOUBLE_TYPE  || "(D)Z"
        Type.BYTE_TYPE    || "(I)Z"
        Type.SHORT_TYPE   || "(I)Z"
        Type.INT_TYPE     || "(I)Z"
    }

    def "contextAppendPrimitiveDescriptor falls back to INT descriptor for non-primitive sorts"() {
        expect:
        AsmDescriptors.contextAppendPrimitiveDescriptor(input) == "(I)Z"

        where:
        input << [
                Type.getType(Object),
                Type.getType("[I"),
                Type.VOID_TYPE,
                Type.getMethodType("()V"),
        ]
    }
}
