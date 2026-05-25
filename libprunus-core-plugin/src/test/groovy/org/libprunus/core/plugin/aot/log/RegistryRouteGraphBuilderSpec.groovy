package org.libprunus.core.plugin.aot.log

import groovy.transform.PackageScope
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.pool.TypePool
import org.libprunus.core.log.annotation.DirectToStringWhitelist
import org.libprunus.core.log.annotation.LogRegistry
import org.libprunus.core.log.annotation.MaxMessageLength
import org.libprunus.core.log.annotation.MethodLoggingField
import org.libprunus.core.log.annotation.MethodLoggingProfile
import org.libprunus.core.log.annotation.MethodLoggingProfiles
import org.libprunus.core.log.annotation.ToStringProfile
import org.libprunus.core.log.annotation.ToStringProfiles
import org.libprunus.core.log.runtime.LogLevel
import org.libprunus.core.plugin.aot.log.fixture.registry.InterfaceFieldExtractorRegistry
import spock.lang.Specification

class RegistryRouteGraphBuilderSpec extends Specification {

    def "build returns a RegistryRouteGraph whose metadata carries the registry binary name, default maxMessageLength and core builtin whitelist when no overriding annotations are present"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(BareRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(BareRegistry.name, locator, typePool)

        then:
        graph.metadata().registryBinaryName() == BareRegistry.name
        graph.metadata().maxMessageLength() == MaxMessageLength.DEFAULT_VALUE
        graph.metadata().directToStringWhitelist() == RuntimeBindingAbi.CORE_BUILTIN_WHITELIST
        graph.methodLoggingRules().isEmpty()
        graph.toStringRules().isEmpty()
    }

    def "build assigns TypeNodeBuilder build as the TypeNodeFactory so nodeOf produces TypeNode equivalent to direct TypeNodeBuilder build invocation"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(BareRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)
        def graph = new RegistryRouteGraphBuilder().build(BareRegistry.name, locator, typePool)
        def sampleType = typePool.describe(SampleClass.name).resolve()

        when:
        def nodeFromGraph = graph.nodeOf(sampleType)
        def nodeFromBuilder = TypeNodeBuilder.build(graph, sampleType)

        then:
        nodeFromGraph.packageName() == nodeFromBuilder.packageName()
        nodeFromGraph.className() == nodeFromBuilder.className()
        nodeFromGraph.methodEligible() == nodeFromBuilder.methodEligible()
        nodeFromGraph.toStringEligible() == nodeFromBuilder.toStringEligible()

        and:
        graph.nodeOf(sampleType).is(nodeFromGraph)
    }

    def "build rejects a class missing LogRegistry annotation with IllegalStateException whose message carries the LogRegistry keyword and the offending class binary name"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(NoLogRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        new RegistryRouteGraphBuilder().build(NoLogRegistry.name, locator, typePool)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@LogRegistry")
        ex.message.contains(NoLogRegistry.name)
        ex.cause == null
    }

    def "build rejects an unknown class name with IllegalStateException carrying the not-found keyword and the offending class name without a cause when resolution returned isResolved false"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(BareRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)
        def absentName = "com.totally.absent.NotAClass"

        when:
        new RegistryRouteGraphBuilder().build(absentName, locator, typePool)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("AOT registry class not found")
        ex.message.contains(absentName)
        ex.cause == null
    }

    def "build wraps an underlying RuntimeException from TypePool describe into IllegalStateException with the original exception as cause"() {
        given:
        def brokenTypePool = new ThrowingTypePool()

        when:
        new RegistryRouteGraphBuilder().build("any.Class", ClassFileLocator.NoOp.INSTANCE, brokenTypePool)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("AOT registry class not found")
        ex.message.contains("any.Class")
        ex.cause instanceof RuntimeException
        ex.cause.message == "boom"
    }

    def "build applies MaxMessageLength value when within bounds and exposes it through metadata maxMessageLength"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(MaxLenRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(MaxLenRegistry.name, locator, typePool)

        then:
        graph.metadata().maxMessageLength() == 2048
        graph.metadata().directToStringWhitelist() == RuntimeBindingAbi.CORE_BUILTIN_WHITELIST
    }

    def "build resolves DirectToStringWhitelist value into list of binary class names preserving declaration order"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(WhitelistedRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(WhitelistedRegistry.name, locator, typePool)

        then:
        graph.metadata().directToStringWhitelist() == ["java.util.List", "java.util.Map", "java.lang.String"]
        graph.metadata().directToStringWhitelist() != RuntimeBindingAbi.CORE_BUILTIN_WHITELIST
    }

    def "build assembles a single MethodLoggingRule from one MethodLoggingProfile with routeId method-route-0 and default INFO INFO levels"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(OneMethodProfileRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(OneMethodProfileRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules().size() == 1
        graph.methodLoggingRules()[0].routeId() == "method-route-0"
        graph.methodLoggingRules()[0].entryLevel() == LogLevel.INFO
        graph.methodLoggingRules()[0].exitLevel() == LogLevel.INFO
        graph.methodLoggingRules()[0].fieldExtractors().isEmpty()
        graph.toStringRules().isEmpty()
    }

    def "build assembles two MethodLoggingRule instances from a repeatable MethodLoggingProfile container with routeIds method-route-0 and method-route-1"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(TwoMethodProfilesRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(TwoMethodProfilesRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules().size() == 2
        graph.methodLoggingRules()*.routeId() == ["method-route-0", "method-route-1"]
    }

    def "build assembles a single ToStringRule from one ToStringProfile with routeId pojo-route-0"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(OneToStringProfileRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(OneToStringProfileRegistry.name, locator, typePool)

        then:
        graph.toStringRules().size() == 1
        graph.toStringRules()[0].routeId() == "pojo-route-0"
        graph.methodLoggingRules().isEmpty()
    }

    def "build assembles two ToStringRule instances from a repeatable ToStringProfile container with routeIds pojo-route-0 and pojo-route-1"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(TwoToStringProfilesRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(TwoToStringProfilesRegistry.name, locator, typePool)

        then:
        graph.toStringRules().size() == 2
        graph.toStringRules()*.routeId() == ["pojo-route-0", "pojo-route-1"]
    }

    def "build applies MethodLoggingProfile entryLevel and exitLevel overrides verbatim into the assembled MethodLoggingRule"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(CustomLevelsRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(CustomLevelsRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules()[0].entryLevel() == LogLevel.DEBUG
        graph.methodLoggingRules()[0].exitLevel() == LogLevel.OFF
    }

    def "build links MethodLoggingRule fieldExtractors to FieldExtractorRef instances resolved via the registry MethodLoggingField table"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(ProfileWithFieldsRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)
        def expectedDescriptor = Type.getMethodDescriptor(ProfileWithFieldsRegistry.getDeclaredMethod("traceId"))

        when:
        def graph = new RegistryRouteGraphBuilder().build(ProfileWithFieldsRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules()[0].fieldExtractors().size() == 1
        def ref = graph.methodLoggingRules()[0].fieldExtractors()[0]
        ref.fieldName() == "traceId"
        ref.ownerInternalName() == Type.getInternalName(ProfileWithFieldsRegistry)
        ref.methodName() == "traceId"
        ref.methodDescriptor() == expectedDescriptor
        ref.isInterface() == false
    }

    def "build assigns isInterface true on FieldExtractorRef when the registry is declared as an interface"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(InterfaceFieldExtractorRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(InterfaceFieldExtractorRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules()[0].fieldExtractors()[0].isInterface() == true
    }

    def "build rejects a profile referencing an unknown MethodLoggingField name with IllegalStateException carrying the profile index, the offending name and the list of available names"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(UnknownFieldRefRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        new RegistryRouteGraphBuilder().build(UnknownFieldRefRegistry.name, locator, typePool)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Profile-0")
        ex.message.contains("unknownX")
        ex.message.contains("Available")
        ex.message.contains("knownY")
    }

    def "build deduplicates profile fields that appear multiple times in MethodLoggingProfile fields preserving first occurrence order"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(DupeFieldRefsRegistry.classLoader)
        def typePool = TypePool.Default.of(locator)

        when:
        def graph = new RegistryRouteGraphBuilder().build(DupeFieldRefsRegistry.name, locator, typePool)

        then:
        graph.methodLoggingRules()[0].fieldExtractors()*.fieldName() == ["a", "b"]
    }

    def "resolveFieldExtractors returns empty map for a registry class with no MethodLoggingField-annotated methods"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(BareRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(BareRegistry.name).resolve()

        when:
        def result = RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        result.size() == 0
        result.isEmpty()
    }

    def "resolveFieldExtractors returns one FieldExtractorRef per MethodLoggingField method preserving declaration order in a multi-extractor registry"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(MultiFieldRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(MultiFieldRegistry.name).resolve()

        when:
        def result = RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        result.keySet() as List == ["a", "b", "c"]
        result.values()*.fieldName() == ["a", "b", "c"]
    }

    def "resolveFieldExtractors rejects a non-public registry class declaring MethodLoggingField with IllegalStateException citing the cross-package invocation requirement"() {
        given:
        def packagePrivateName = "org.libprunus.core.plugin.aot.log.fixture.registry.PackagePrivateFieldRegistry"
        def packagePrivateClass = Class.forName(packagePrivateName)
        def locator = ClassFileLocator.ForClassLoader.of(packagePrivateClass.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(packagePrivateName).resolve()

        when:
        RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("AOT registry class defining @MethodLoggingField must be public to allow cross-package invocations")
        ex.message.contains(packagePrivateName)
    }

    def "resolveFieldExtractors rejects a non-public MethodLoggingField method with IllegalStateException citing must be public"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(NonPublicExtractorRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(NonPublicExtractorRegistry.name).resolve()

        when:
        RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@MethodLoggingField method must be public")
        ex.message.contains("#x")
    }

    def "resolveFieldExtractors rejects a non-static MethodLoggingField method with IllegalStateException citing must be static"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(NonStaticExtractorRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(NonStaticExtractorRegistry.name).resolve()

        when:
        RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@MethodLoggingField method must be static")
        ex.message.contains("#x")
    }

    def "resolveFieldExtractors rejects a MethodLoggingField method declaring parameters with IllegalStateException citing must have no parameters"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(WithParamsExtractorRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(WithParamsExtractorRegistry.name).resolve()

        when:
        RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@MethodLoggingField method must have no parameters")
        ex.message.contains("#x")
    }

    def "resolveFieldExtractors rejects a void-returning MethodLoggingField method with IllegalStateException citing must not return void"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(VoidExtractorRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(VoidExtractorRegistry.name).resolve()

        when:
        RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@MethodLoggingField method must not return void")
        ex.message.contains("#x")
    }

    def "resolveFieldExtractors rejects two MethodLoggingField methods sharing the same field name with IllegalStateException citing the duplicate name and the registry class name"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(DuplicateFieldNameRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(DuplicateFieldNameRegistry.name).resolve()

        when:
        RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Duplicate @MethodLoggingField name 'dup' in ")
        ex.message.contains(DuplicateFieldNameRegistry.name)
    }

    def "resolveFieldExtractors derives FieldExtractorRef ownerInternalName from the registry class internal binary path using slashes not dots"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(MultiFieldRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(MultiFieldRegistry.name).resolve()

        when:
        def result = RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        result["a"].ownerInternalName() == Type.getInternalName(MultiFieldRegistry)
        !result["a"].ownerInternalName().contains(".")
        result["a"].ownerInternalName().contains("/")
    }

    def "resolveFieldExtractors derives FieldExtractorRef methodDescriptor from JVM descriptor of the extractor method preserving primitive and reference return types"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(PrimitiveAndReferenceExtractorsRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(PrimitiveAndReferenceExtractorsRegistry.name).resolve()
        def expectedIntDescriptor = Type.getMethodDescriptor(PrimitiveAndReferenceExtractorsRegistry.getDeclaredMethod("i"))
        def expectedStrDescriptor = Type.getMethodDescriptor(PrimitiveAndReferenceExtractorsRegistry.getDeclaredMethod("s"))

        when:
        def result = RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        result["intField"].methodDescriptor() == expectedIntDescriptor
        result["strField"].methodDescriptor() == expectedStrDescriptor
        result["intField"].methodDescriptor() == "()I"
        result["strField"].methodDescriptor() == "()Ljava/lang/String;"
    }

    def "resolveFieldExtractors skips methods without MethodLoggingField annotation even when sharing names with extractor methods"() {
        given:
        def locator = ClassFileLocator.ForClassLoader.of(MixedExtractorAndPlainRegistry.classLoader)
        def typeDesc = TypePool.Default.of(locator).describe(MixedExtractorAndPlainRegistry.name).resolve()

        when:
        def result = RegistryRouteGraphBuilder.resolveFieldExtractors(typeDesc)

        then:
        result.keySet() as List == ["x"]
        result["x"].methodName() == "x"
        !result.keySet().any { it.startsWith("other") }
        !result.keySet().any { it.startsWith("instance") }
    }

    private static class ThrowingTypePool implements TypePool {

        @Override
        TypePool.Resolution describe(String name) {
            throw new RuntimeException("boom")
        }

        @Override
        void clear() {}
    }

    @LogRegistry
    static class BareRegistry {}

    static class NoLogRegistry {}

    static class SampleClass {
        public String label
    }

    @LogRegistry
    @MaxMessageLength(2048)
    static class MaxLenRegistry {}

    @LogRegistry
    @DirectToStringWhitelist([List, Map, String])
    static class WhitelistedRegistry {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["sample.svc"],
            includeClassSuffixes = ["Service"])
    static class OneMethodProfileRegistry {}

    @LogRegistry
    @MethodLoggingProfiles([
            @MethodLoggingProfile(
                    includePackages = ["sample.svc"],
                    includeClassSuffixes = ["Service"]),
            @MethodLoggingProfile(
                    includePackages = ["sample.api"],
                    includeClassSuffixes = ["Controller"])
    ])
    static class TwoMethodProfilesRegistry {}

    @LogRegistry
    @ToStringProfile(
            includePackages = ["sample.dto"],
            includeClassSuffixes = ["Dto"])
    static class OneToStringProfileRegistry {}

    @LogRegistry
    @ToStringProfiles([
            @ToStringProfile(
                    includePackages = ["sample.dto"],
                    includeClassSuffixes = ["Dto"]),
            @ToStringProfile(
                    includePackages = ["sample.response"],
                    includeClassSuffixes = ["Response"])
    ])
    static class TwoToStringProfilesRegistry {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["x"],
            includeClassSuffixes = ["S"],
            entryLevel = LogLevel.DEBUG,
            exitLevel = LogLevel.OFF)
    static class CustomLevelsRegistry {}

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["x"],
            includeClassSuffixes = ["S"],
            fields = ["traceId"])
    static class ProfileWithFieldsRegistry {

        @MethodLoggingField("traceId")
        public static String traceId() {
            return "trace"
        }
    }

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["x"],
            includeClassSuffixes = ["S"],
            fields = ["unknownX"])
    static class UnknownFieldRefRegistry {

        @MethodLoggingField("knownY")
        public static String y() {
            return "y"
        }
    }

    @LogRegistry
    @MethodLoggingProfile(
            includePackages = ["x"],
            includeClassSuffixes = ["S"],
            fields = ["a", "b", "a"])
    static class DupeFieldRefsRegistry {

        @MethodLoggingField("a")
        public static String a() {
            return "a"
        }

        @MethodLoggingField("b")
        public static String b() {
            return "b"
        }
    }

    @LogRegistry
    public static class MultiFieldRegistry {

        @MethodLoggingField("a")
        public static String a() {
            return "a"
        }

        @MethodLoggingField("b")
        public static String b() {
            return "b"
        }

        @MethodLoggingField("c")
        public static String c() {
            return "c"
        }
    }

    @LogRegistry
    public static class NonPublicExtractorRegistry {

        @MethodLoggingField("x")
        @PackageScope
        static String x() {
            return "x"
        }
    }

    @LogRegistry
    public static class NonStaticExtractorRegistry {

        @MethodLoggingField("x")
        public String x() {
            return "x"
        }
    }

    @LogRegistry
    public static class WithParamsExtractorRegistry {

        @MethodLoggingField("x")
        public static String x(int v) {
            return Integer.toString(v)
        }
    }

    @LogRegistry
    public static class VoidExtractorRegistry {

        @MethodLoggingField("x")
        public static void x() {}
    }

    @LogRegistry
    public static class DuplicateFieldNameRegistry {

        @MethodLoggingField("dup")
        public static String a() {
            return "a"
        }

        @MethodLoggingField("dup")
        public static String b() {
            return "b"
        }
    }

    @LogRegistry
    public static class PrimitiveAndReferenceExtractorsRegistry {

        @MethodLoggingField("intField")
        public static int i() {
            return 42
        }

        @MethodLoggingField("strField")
        public static String s() {
            return "s"
        }
    }

    @LogRegistry
    public static class MixedExtractorAndPlainRegistry {

        @MethodLoggingField("x")
        public static String x() {
            return "x"
        }

        public static String otherUnannotated() {
            return "other"
        }

        public String instanceMethod() {
            return "i"
        }
    }
}
