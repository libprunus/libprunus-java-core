package org.libprunus.core.plugin.aot.log

import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.pool.TypePool
import org.libprunus.core.log.annotation.DirectToStringWhitelist
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MaxMessageLength
import org.libprunus.core.log.annotation.MethodLoggingProfile
import spock.lang.Specification

class RegistryRouteGraphBuilderAlgorithmSpec extends Specification {

    def "build resolves MaxMessageLength value through normalizeMaxMessageLength producing the expected metadata length for boundary inputs"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(fixtureClass.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(fixtureClass.name, locator, typePool)

        then:
        graph.metadata().maxMessageLength() == expectedNormalized
        graph.metadata().directToStringWhitelist() == RuntimeBindingAbi.CORE_BUILTIN_WHITELIST

        where:
        fixtureClass     | expectedNormalized
        MaxLen0          | MaxMessageLength.MIN_VALUE
        MaxLen15         | MaxMessageLength.MIN_VALUE
        MaxLen16         | MaxMessageLength.MIN_VALUE
        MaxLen17         | 17
        MaxLen512        | MaxMessageLength.DEFAULT_VALUE
        MaxLenHardLimit  | MaxMessageLength.MAX_VALUE
    }

    def "build rejects MaxMessageLength negative value with IllegalStateException citing must be non-negative requirement and the registry class binary name"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(NegativeMaxLenRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        new RegistryRouteGraphBuilder().build(NegativeMaxLenRegistry.name, locator, typePool)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@MaxMessageLength value must be >= 0 on ")
        ex.message.contains(NegativeMaxLenRegistry.name)
        ex.message.contains(": -1")
    }

    def "build rejects MaxMessageLength above hard limit with IllegalStateException citing the upper bound and the offending value"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(OverLimitMaxLenRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        new RegistryRouteGraphBuilder().build(OverLimitMaxLenRegistry.name, locator, typePool)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@MaxMessageLength value must be <= " + MaxMessageLength.MAX_VALUE)
        ex.message.contains(OverLimitMaxLenRegistry.name)
        ex.message.contains(": " + (MaxMessageLength.MAX_VALUE + 1))
    }

    def "build through filterBlankEntries strips blank and all-whitespace entries from MethodLoggingProfile includePackages preserving trimmed non-blank entries in order"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(BlankEntriesProfileRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(BlankEntriesProfileRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules()[0].@includePackages == ["com.x", "com.y"]
        graph.methodLoggingRules()[0].@excludePackages == []
    }

    def "build through resolveProfileFields returns empty extractor list when profile fields array is empty but the profile itself is still assembled"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(EmptyFieldRefsRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(EmptyFieldRefsRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules().size() == 1
        graph.methodLoggingRules()[0].fieldExtractors().isEmpty()
    }

    def "build through resolveWhitelist returns an empty immutable list when DirectToStringWhitelist value array is empty"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(EmptyWhitelistRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(EmptyWhitelistRegistry.name, locator, typePool)
        def whitelist = graph.metadata().directToStringWhitelist()

        then:
        whitelist.isEmpty()

        when:
        whitelist.add("late.Addition")

        then:
        thrown(UnsupportedOperationException)
    }

    def "build through resolveWhitelist deduplicates repeated class entries preserving first occurrence order"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(DuplicateWhitelistRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(DuplicateWhitelistRegistry.name, locator, typePool)

        then:
        graph.metadata().directToStringWhitelist() == ["java.util.List", "java.util.Map", "java.util.Set"]
    }

    @LogRegistry
    @MaxMessageLength(0)
    static class MaxLen0 {}

    @LogRegistry
    @MaxMessageLength(15)
    static class MaxLen15 {}

    @LogRegistry
    @MaxMessageLength(16)
    static class MaxLen16 {}

    @LogRegistry
    @MaxMessageLength(17)
    static class MaxLen17 {}

    @LogRegistry
    @MaxMessageLength(512)
    static class MaxLen512 {}

    @LogRegistry
    @MaxMessageLength(1_048_576)
    static class MaxLenHardLimit {}

    @LogRegistry
    @MaxMessageLength(-1)
    static class NegativeMaxLenRegistry {}

    @LogRegistry
    @MaxMessageLength(1_048_577)
    static class OverLimitMaxLenRegistry {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["  ", "", "  com.x  ", "com.y"],
            includeClassSuffixes = ["S"])
    static class BlankEntriesProfileRegistry {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["x"],
            includeClassSuffixes = ["S"])
    static class EmptyFieldRefsRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([])
    static class EmptyWhitelistRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([List, Map, List, Set])
    static class DuplicateWhitelistRegistry {}
}
