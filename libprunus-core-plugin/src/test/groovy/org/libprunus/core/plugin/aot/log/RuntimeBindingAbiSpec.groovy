package org.libprunus.core.plugin.aot.log

import java.lang.reflect.Modifier
import net.bytebuddy.jar.asm.Type
import org.libprunus.core.log.annotation.DirectToStringWhitelist
import org.libprunus.core.log.runtime.AbstractLogConfig
import org.libprunus.core.log.runtime.LogRuntime
import spock.lang.Specification

class RuntimeBindingAbiSpec extends Specification {

    def "AOT_RUNTIME_INTERNAL_NAME identifies LogRuntime in JVM internal name form"() {
        expect:
        RuntimeBindingAbi.AOT_RUNTIME_INTERNAL_NAME == "org/libprunus/core/log/runtime/LogRuntime"
        RuntimeBindingAbi.AOT_RUNTIME_INTERNAL_NAME == LogRuntime.class.name.replace('.', '/')
    }

    def "AOT_RUNTIME_INTERNAL_NAME mirrors WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME for single LogRuntime identification"() {
        expect:
        RuntimeBindingAbi.AOT_RUNTIME_INTERNAL_NAME == WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME
    }

    def "RUNTIME_BUILD_WHITELIST_CACHE_METHOD and descriptor match runtime LogRuntime ABI"() {
        given:
        def method = LogRuntime.class.getDeclaredMethod(
                RuntimeBindingAbi.RUNTIME_BUILD_WHITELIST_CACHE_METHOD, String[].class)

        expect:
        RuntimeBindingAbi.RUNTIME_BUILD_WHITELIST_CACHE_METHOD == "buildWhitelistCache"
        RuntimeBindingAbi.RUNTIME_BUILD_WHITELIST_CACHE_DESCRIPTOR == "([Ljava/lang/String;)Ljava/lang/ClassValue;"
        Type.getMethodDescriptor(method) == RuntimeBindingAbi.RUNTIME_BUILD_WHITELIST_CACHE_DESCRIPTOR
        method.returnType == ClassValue.class
        Modifier.isStatic(method.modifiers)
    }

    def "RUNTIME_IS_WHITELISTED_CACHED_METHOD and descriptor match runtime LogRuntime ABI"() {
        given:
        def method = LogRuntime.class.getDeclaredMethod(
                RuntimeBindingAbi.RUNTIME_IS_WHITELISTED_CACHED_METHOD, Class.class, ClassValue.class)

        expect:
        RuntimeBindingAbi.RUNTIME_IS_WHITELISTED_CACHED_METHOD == "isWhitelistedCached"
        RuntimeBindingAbi.RUNTIME_IS_WHITELISTED_CACHED_DESCRIPTOR == "(Ljava/lang/Class;Ljava/lang/ClassValue;)Z"
        Type.getMethodDescriptor(method) == RuntimeBindingAbi.RUNTIME_IS_WHITELISTED_CACHED_DESCRIPTOR
        method.returnType == boolean.class
        Modifier.isStatic(method.modifiers)
    }

    def "INITIALIZE_BINDING_METHOD names the runtime initialize entry point"() {
        given:
        def method = LogRuntime.class.getDeclaredMethod(
                RuntimeBindingAbi.INITIALIZE_BINDING_METHOD, AbstractLogConfig.class)

        expect:
        RuntimeBindingAbi.INITIALIZE_BINDING_METHOD == "initializeBinding"
        method.returnType == void.class
        Modifier.isStatic(method.modifiers)
    }

    def "CORE_BUILTIN_WHITELIST derives from DirectToStringWhitelist.CORE_BUILTIN preserving declaration order"() {
        given:
        def expected = Arrays.stream(DirectToStringWhitelist.CORE_BUILTIN).map { it.name }.toList()

        expect:
        RuntimeBindingAbi.CORE_BUILTIN_WHITELIST == expected
    }

    def "CORE_BUILTIN_WHITELIST is immutable and rejects mutation"() {
        given:
        def snapshot = new ArrayList<>(RuntimeBindingAbi.CORE_BUILTIN_WHITELIST)

        when:
        RuntimeBindingAbi.CORE_BUILTIN_WHITELIST.add("x")

        then:
        thrown(UnsupportedOperationException)
        RuntimeBindingAbi.CORE_BUILTIN_WHITELIST == snapshot

        when:
        RuntimeBindingAbi.CORE_BUILTIN_WHITELIST.remove(0)

        then:
        thrown(UnsupportedOperationException)
        RuntimeBindingAbi.CORE_BUILTIN_WHITELIST == snapshot
    }

    def "private constructor throws UnsupportedOperationException to enforce non-instantiability"() {
        when:
        new RuntimeBindingAbi()

        then:
        thrown(UnsupportedOperationException)
    }
}
