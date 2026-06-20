package org.libprunus.core.plugin.aot.log

import org.libprunus.core.log.runtime.AbstractLogConfig
import spock.lang.Specification

class BindingClassGeneratorLoadIntegrationSpec extends Specification {

    def "generateBytes produces a loadable binding class for a mid-scale whitelist with positive and negative membership"() {
        given:
        def whitelist = (1..1000).collect { "java.util.HashMap${it}".toString() }
        whitelist << "java.util.Date"

        when:
        def binding = loadBinding(whitelist)

        then:
        binding.isWhitelisted(Date)
        !binding.isWhitelisted(String)
    }

    def "isWhitelisted dispatches to LogRuntime cache covering direct match, superclass and superinterface walk, miss, null type and empty whitelist"() {
        expect:
        loadBinding(whitelistNames).isWhitelisted(type) == expected

        where:
        whitelistNames             | type         || expected
        ["java.util.Date"]         | Date         || true
        ["java.lang.Number"]       | Number       || true
        ["java.lang.Number"]       | Long         || true
        ["java.lang.CharSequence"] | String       || true
        ["java.util.Date"]         | ArrayList    || false
        ["java.util.Date"]         | (Class) null || false
        []                         | Date         || false
    }

    def "loaded binding returns the maxMessageLength supplied to generateBytes verbatim across signed pushInt tiers"() {
        expect:
        loadBinding([], maxLen).getMaxMessageLength() == maxLen

        where:
        maxLen << [0, 5, 6, 127, 128, 32767, 32768, Integer.MAX_VALUE, -1, Integer.MIN_VALUE]
    }

    private AbstractLogConfig loadBinding(List<String> whitelistNames, int maxMessageLength = 128) {
        def className = "org.libprunus.aot.generated.test.BindingTestImpl"
        def bytes = new BindingClassGenerator().generateBytes(className, "21", maxMessageLength, whitelistNames)
        def loader = new ClassLoader(getClass().classLoader) {
            @Override
            Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == className) {
                    return defineClass(name, bytes, 0, bytes.length)
                }
                throw new ClassNotFoundException(name)
            }
        }
        return loader.loadClass(className).getDeclaredConstructor().newInstance() as AbstractLogConfig
    }
}
