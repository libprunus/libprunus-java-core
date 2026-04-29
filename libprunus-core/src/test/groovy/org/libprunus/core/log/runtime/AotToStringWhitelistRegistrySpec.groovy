package org.libprunus.core.log.runtime

import spock.lang.Specification

class AotToStringWhitelistRegistrySpec extends Specification {

    def "tryLoad returns empty array - #scenario"() {
        when: "tryLoad is invoked with the configured classloader"
        Class<?>[] loaded = AotToStringWhitelistRegistry.tryLoad(loader)

        then: "an empty array is returned regardless of the failure mode"
        loaded.length == 0

        where:
        scenario            | loader
        'null classloader'  | null
        'class absent'      | new GroovyClassLoader(AotToStringWhitelistRegistrySpec.class.classLoader)
        'no get method'     | proxyWith('static def other() {}')
        'get returns null'  | proxyWith('static Class[] get() { null }')
        'get returns empty' | proxyWith('static Class[] get() { [] as Class[] }')
        'wrong return type' | proxyWith('static Object get() { "unsupported" }')
        'get throws'        | proxyWith('static Class[] get() { throw new IllegalStateException("boom") }')
        'linkage in clinit' | proxyWith("static { throw new LinkageError('x') }\n    static Class[] get() { [] as Class[] }")
    }

    def "tryLoad returns the configured types when proxy exposes class array"() {
        given: "a loader providing a proxy that exposes a non-empty class array"
        GroovyClassLoader loader = proxyWith('static Class[] get() { [String, Number] as Class[] }')

        when: "tryLoad is invoked"
        Class<?>[] loaded = AotToStringWhitelistRegistry.tryLoad(loader)

        then: "the returned array contains exactly the configured types"
        loaded as List == [String, Number]
    }

    def "tryLoad returns an independent clone on each invocation"() {
        given: "a loader whose proxy returns a class array"
        GroovyClassLoader loader = proxyWith('static Class[] get() { [String] as Class[] }')

        when: "tryLoad is called twice with the same loader"
        Class<?>[] first = AotToStringWhitelistRegistry.tryLoad(loader)
        Class<?>[] second = AotToStringWhitelistRegistry.tryLoad(loader)

        then: "each call produces a distinct array object"
        !first.is(second)
    }

    def "tryLoad propagates NullPointerException when generated get method is not static"() {
        given: "a loader providing a class whose get() is declared as an instance method"
        GroovyClassLoader loader = proxyWith('Class[] get() { [String] as Class[] }')

        when: "tryLoad invokes get() with null as the receiver for an instance method"
        AotToStringWhitelistRegistry.tryLoad(loader)

        then: "NullPointerException escapes all catch blocks and propagates to the caller"
        thrown(NullPointerException)
    }

    def "resolveToStringWhitelistTypes returns the same cached reference on repeated calls"() {
        when: "resolveToStringWhitelistTypes is called multiple times"
        Class<?>[] first = AotToStringWhitelistRegistry.resolveToStringWhitelistTypes()
        Class<?>[] second = AotToStringWhitelistRegistry.resolveToStringWhitelistTypes()

        then: "both invocations return the identical cached CUSTOM_TYPES reference without copying"
        first.is(second)
    }

    def "loadToStringWhitelistTypes falls back to thread context classloader when registry loader misses"() {
        given: "a context classloader distinct from the registry loader that contains the generated proxy"
        ClassLoader original = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = proxyWith('static Class[] get() { [CharSequence] as Class[] }')

        when: "loadToStringWhitelistTypes is evaluated"
        Class<?>[] loaded = AotToStringWhitelistRegistry.loadToStringWhitelistTypes()

        then: "context fallback returns the configured types"
        loaded as List == [CharSequence]

        cleanup:
        Thread.currentThread().contextClassLoader = original
    }

    def "loadToStringWhitelistTypes does not try context fallback when context equals registry loader"() {
        given: "the context classloader is set to the same instance as the registry classloader"
        ClassLoader original = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = AotToStringWhitelistRegistry.class.classLoader

        when: "loadToStringWhitelistTypes is evaluated"
        Class<?>[] loaded = AotToStringWhitelistRegistry.loadToStringWhitelistTypes()

        then: "empty result is returned without attempting context fallback"
        loaded.length == 0

        cleanup:
        Thread.currentThread().contextClassLoader = original
    }

    def "loadToStringWhitelistTypes returns empty when both distinct loaders miss the generated class"() {
        given: "a context classloader distinct from the registry loader and without the generated proxy"
        ClassLoader original = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = new GroovyClassLoader(this.class.classLoader)

        when: "loadToStringWhitelistTypes is invoked with both loaders missing the proxy"
        Class<?>[] loaded = AotToStringWhitelistRegistry.loadToStringWhitelistTypes()

        then: "empty is returned since neither loader provides the generated class"
        loaded.length == 0

        cleanup:
        Thread.currentThread().contextClassLoader = original
    }

    private static GroovyClassLoader proxyWith(String body) {
        generatedProxyLoader("""
package org.libprunus.generated
class AotToStringWhitelist {
    ${body}
}
""")
    }

    private static GroovyClassLoader generatedProxyLoader(String source) {
        GroovyClassLoader loader = new GroovyClassLoader(AotToStringWhitelistRegistrySpec.class.classLoader)
        loader.parseClass(source)
        loader
    }
}
