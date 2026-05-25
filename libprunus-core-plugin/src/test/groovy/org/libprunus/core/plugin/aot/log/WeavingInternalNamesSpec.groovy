package org.libprunus.core.plugin.aot.log

import java.lang.reflect.Modifier
import net.bytebuddy.jar.asm.Type
import org.libprunus.core.log.runtime.LogRuntime
import org.libprunus.core.log.runtime.Loggable
import org.libprunus.core.log.runtime.StringBuilderPool
import org.libprunus.core.log.runtime.StringBuilderWithContext
import spock.lang.Specification

class WeavingInternalNamesSpec extends Specification {

    def "AOT_RUNTIME_INTERNAL_NAME equals JVM internal name of LogRuntime runtime class"() {
        expect:
        WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME == LogRuntime.name.replace('.', '/')
        WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME == "org/libprunus/core/log/runtime/LogRuntime"
        WeavingInternalNames.AOT_RUNTIME_INTERNAL_NAME != LogRuntime.name
    }

    def "AOT_LOGGABLE_BINARY_NAME equals binary name of Loggable runtime interface"() {
        expect:
        WeavingInternalNames.AOT_LOGGABLE_BINARY_NAME == Loggable.name
        WeavingInternalNames.AOT_LOGGABLE_BINARY_NAME == "org.libprunus.core.log.runtime.Loggable"
        WeavingInternalNames.AOT_LOGGABLE_BINARY_NAME != Loggable.name.replace('.', '/')
    }

    def "STRING_BUILDER_POOL_INTERNAL_NAME equals JVM internal name of StringBuilderPool runtime class"() {
        expect:
        WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME == StringBuilderPool.name.replace('.', '/')
        WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME == "org/libprunus/core/log/runtime/StringBuilderPool"
        WeavingInternalNames.STRING_BUILDER_POOL_INTERNAL_NAME != StringBuilderPool.name
    }

    def "AOT_RENDER_METHOD names the single abstract render method on Loggable"() {
        given:
        def method = Loggable.getDeclaredMethod(
                WeavingInternalNames.AOT_RENDER_METHOD, StringBuilderWithContext)

        expect:
        WeavingInternalNames.AOT_RENDER_METHOD == "_libprunus_render"
        method.name == WeavingInternalNames.AOT_RENDER_METHOD
        Modifier.isAbstract(method.modifiers)
        method.returnType == void.class
    }

    def "AOT_RENDER_DESCRIPTOR equals JVM descriptor of Loggable#_libprunus_render"() {
        given:
        def method = Loggable.getDeclaredMethod(
                WeavingInternalNames.AOT_RENDER_METHOD, StringBuilderWithContext)

        expect:
        WeavingInternalNames.AOT_RENDER_DESCRIPTOR == Type.getMethodDescriptor(method)
        WeavingInternalNames.AOT_RENDER_DESCRIPTOR == "(Lorg/libprunus/core/log/runtime/StringBuilderWithContext;)V"
        WeavingInternalNames.AOT_RENDER_DESCRIPTOR != "()V"
        WeavingInternalNames.AOT_RENDER_DESCRIPTOR != "(Ljava/lang/Object;)V"
    }

    def "MASK_SENTINEL is the three-asterisk literal documented on @Sensitive"() {
        expect:
        WeavingInternalNames.MASK_SENTINEL == "***"
        WeavingInternalNames.MASK_SENTINEL.length() == 3
        !WeavingInternalNames.MASK_SENTINEL.isEmpty()
        WeavingInternalNames.MASK_SENTINEL.every { it == ('*' as char) }
    }

    def "SYNTHETIC_ENTER_PREFIX is the lp-marker enter prefix terminated by dollar"() {
        expect:
        WeavingInternalNames.SYNTHETIC_ENTER_PREFIX == '$lp$enter$'
        WeavingInternalNames.SYNTHETIC_ENTER_PREFIX.endsWith('$')
        WeavingInternalNames.SYNTHETIC_ENTER_PREFIX != WeavingInternalNames.SYNTHETIC_EXIT_PREFIX
    }

    def "SYNTHETIC_EXIT_PREFIX is the lp-marker exit prefix terminated by dollar"() {
        expect:
        WeavingInternalNames.SYNTHETIC_EXIT_PREFIX == '$lp$exit$'
        WeavingInternalNames.SYNTHETIC_EXIT_PREFIX.endsWith('$')
        WeavingInternalNames.SYNTHETIC_EXIT_PREFIX != WeavingInternalNames.SYNTHETIC_ENTER_PREFIX
        !WeavingInternalNames.SYNTHETIC_EXIT_PREFIX.startsWith(WeavingInternalNames.SYNTHETIC_ENRICH_METHOD)
        !WeavingInternalNames.SYNTHETIC_ENRICH_METHOD.startsWith(WeavingInternalNames.SYNTHETIC_EXIT_PREFIX)
    }

    def "SYNTHETIC_ENRICH_METHOD is the fixed enrich method name without trailing dollar"() {
        expect:
        WeavingInternalNames.SYNTHETIC_ENRICH_METHOD == '$lp$enrich'
        !WeavingInternalNames.SYNTHETIC_ENRICH_METHOD.endsWith('$')
        WeavingInternalNames.SYNTHETIC_ENRICH_METHOD != WeavingInternalNames.SYNTHETIC_ENTER_PREFIX
        WeavingInternalNames.SYNTHETIC_ENRICH_METHOD != WeavingInternalNames.SYNTHETIC_EXIT_PREFIX
    }

    def "isSyntheticMethodName returns true for synthetic enter, exit and enrich method names"() {
        expect:
        WeavingInternalNames.isSyntheticMethodName(name)

        where:
        name << [
                '$lp$enter$foo',
                '$lp$enter$',
                '$lp$exit$foo',
                '$lp$exit$',
                '$lp$enrich',
        ]
    }

    def "isSyntheticMethodName returns false for non-synthetic names including prefix-overlap boundaries"() {
        expect:
        !WeavingInternalNames.isSyntheticMethodName(name)

        where:
        name << [
                'compute',
                '',
                '$lp$',
                '$lp$enter',
                '$lp$exit',
                '$lp$enrich$x',
                'lp$enter$foo',
                ' $lp$enter$foo',
        ]
    }
}
