package org.libprunus.core.plugin.aot.log

import java.util.Optional
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool
import org.libprunus.core.log.annotation.DoNotLog
import org.libprunus.core.log.annotation.Sensitive
import spock.lang.Specification

class MethodFamilyResolverSpec extends Specification {

    private static final TypePool TYPE_POOL =
            TypePool.Default.of(ClassFileLocator.ForClassLoader.of(MethodFamilyResolverSpec.classLoader))

    private static TypeDescription describe(Class<?> owner) {
        TYPE_POOL.describe(owner.getName()).resolve()
    }

    private static MethodDescription declaredMethod(Class<?> owner, String name) {
        describe(owner).getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static MethodDescription declaredMethod(Class<?> owner, String name, Class<?> paramType) {
        describe(owner).getDeclaredMethods()
                .filter(ElementMatchers.named(name).and(ElementMatchers.takesArguments(paramType)))
                .getOnly()
    }

    private static MethodDescription declaredConstructor(Class<?> owner) {
        describe(owner).getDeclaredMethods()
                .filter(ElementMatchers.isConstructor())
                .getOnly()
    }

    def "analyzeOverrideChain returns EMPTY for static methods so non-instance dispatch never enters BFS"() {
        given:
        def method = declaredMethod(StaticHolder, "utility")

        when:
        def analysis = MethodFamilyResolver.analyzeOverrideChain(method)

        then:
        !analysis.isOverride()
        analysis.layers().isEmpty()
        analysis.is(MethodFamilyResolver.OverrideChainAnalysis.EMPTY)
    }

    def "analyzeOverrideChain returns EMPTY for private methods so non-overridable bodies never enter BFS"() {
        given:
        def method = declaredMethod(PrivateHolder, "secret")

        when:
        def analysis = MethodFamilyResolver.analyzeOverrideChain(method)

        then:
        !analysis.isOverride()
        analysis.layers().isEmpty()
        analysis.is(MethodFamilyResolver.OverrideChainAnalysis.EMPTY)
    }

    def "analyzeOverrideChain returns EMPTY for constructors so synthetic init paths never enter BFS"() {
        given:
        def ctor = declaredConstructor(CtorOnly)

        when:
        def analysis = MethodFamilyResolver.analyzeOverrideChain(ctor)

        then:
        !analysis.isOverride()
        analysis.layers().isEmpty()
        analysis.is(MethodFamilyResolver.OverrideChainAnalysis.EMPTY)
    }

    def "analyzeOverrideChain returns EMPTY for a method with no matching supertype signature so non-override stays out of BFS"() {
        given:
        def method = declaredMethod(NonOverrideSubject, "uniquelyOwn")

        when:
        def analysis = MethodFamilyResolver.analyzeOverrideChain(method)

        then:
        !analysis.isOverride()
        analysis.layers().isEmpty()
        analysis.is(MethodFamilyResolver.OverrideChainAnalysis.EMPTY)
    }

    def "analyzeOverrideChain reports single-layer ancestor when only direct supertype declares same signature"() {
        given:
        def method = declaredMethod(SingleOverrideChild, "compute")

        when:
        def analysis = MethodFamilyResolver.analyzeOverrideChain(method)

        then:
        analysis.isOverride()
        analysis.layers().size() == 1
        analysis.layers()[0].size() == 1
        analysis.layers()[0][0].getDeclaringType().asErasure().getName() == SingleOverrideParent.getName()
    }

    def "analyzeOverrideChain partitions multiple ancestors into topological layers so closer types vote before farther types"() {
        given:
        def method = declaredMethod(MultiLayerChild, "compute")

        when:
        def analysis = MethodFamilyResolver.analyzeOverrideChain(method)
        def layerOwnerNames = analysis.layers().collect { layer ->
            layer.collect { md -> md.getDeclaringType().asErasure().getName() } as Set
        }

        then:
        analysis.isOverride()
        analysis.layers().size() == 2
        layerOwnerNames[0] == [MultiLayerParent.getName()] as Set
        layerOwnerNames[1] == [MultiLayerGrandparent.getName()] as Set
    }

    def "resolveEffectiveMethodFamily short-circuits at layer 1 method-level annotation even when override chain carries conflicting ancestor family"() {
        given:
        def subject = declaredMethod(MethodLevelMaskedOverrideOfSuppressedAncestor, "compute")
        def chain = MethodFamilyResolver.analyzeOverrideChain(subject)
        def graph = graphWithDeclaredMethodsFromLayers(chain.layers())

        when:
        def family = MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), graph)

        then:
        chain.isOverride()
        family == Family.MASK
    }

    def "resolveEffectiveMethodFamily falls back to declaringTypeFamily when method has no method-level annotation"() {
        given:
        def method = declaredMethod(NonOverrideSubject, "uniquelyOwn")
        def graph = graphWithEmptyDeclaredMethods()

        when:
        def family = MethodFamilyResolver.resolveEffectiveMethodFamily(
                method, Family.SUPPRESS, false, List.of(), graph)

        then:
        family == Family.SUPPRESS
    }

    def "resolveEffectiveMethodFamily returns NONE when not an override and no closer annotation contributes"() {
        given:
        def method = declaredMethod(NonOverrideSubject, "uniquelyOwn")
        def graph = graphWithEmptyDeclaredMethods()

        when:
        def family = MethodFamilyResolver.resolveEffectiveMethodFamily(
                method, Family.NONE, false, List.of(), graph)

        then:
        family == Family.NONE
    }

    def "resolveEffectiveMethodFamily walks override layers when subject has no closer family annotation"() {
        given:
        def subject = declaredMethod(OverrideMaskedChild, "compute")
        def chain = MethodFamilyResolver.analyzeOverrideChain(subject)
        def graph = graphWithDeclaredMethodsFromLayers(chain.layers())

        when:
        def family = MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), graph)

        then:
        chain.isOverride()
        family == Family.MASK
    }

    def "resolveEffectiveMethodFamily returns NONE when override chain is exhausted with no contributing layer"() {
        given:
        def subject = declaredMethod(SingleOverrideChild, "compute")
        def chain = MethodFamilyResolver.analyzeOverrideChain(subject)
        def graph = graphWithDeclaredMethodsFromLayers(chain.layers())

        when:
        def family = MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), graph)

        then:
        chain.isOverride()
        family == Family.NONE
    }

    def "resolveEffectiveMethodFamily falls back to ancestor type-level family when ancestor method-level family is absent"() {
        given:
        def subject = declaredMethod(TypeLevelMaskedChild, "compute")
        def chain = MethodFamilyResolver.analyzeOverrideChain(subject)
        def graph = graphWithDeclaredMethodsFromLayers(chain.layers())

        when:
        def family = MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), graph)

        then:
        chain.isOverride()
        family == Family.MASK
    }

    def "merge of same-family ancestor votes returns that family without surfacing originDescriber output"() {
        given:
        def subject = declaredMethod(SameLayerSameFamilyChild, "compute")
        def chain = MethodFamilyResolver.analyzeOverrideChain(subject)
        def graph = graphWithDeclaredMethodsFromLayers(chain.layers())

        when:
        def family = MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), graph)

        then:
        chain.isOverride()
        chain.layers().size() == 1
        chain.layers()[0].size() == 2
        family == Family.MASK
    }

    def "merge of different-family ancestor votes at the same layer throws IllegalStateException with contract keywords and origin describer fields"() {
        given:
        def subject = declaredMethod(SameLayerConflictChild, "compute")
        def chain = MethodFamilyResolver.analyzeOverrideChain(subject)
        def graph = graphWithDeclaredMethodsFromLayers(chain.layers())

        when:
        MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), graph)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Same-layer multi-family conflict")
        ex.message.contains("mutually exclusive")
        ex.message.contains("configuration error")
        ex.message.contains(SameLayerConflictChild.name + "#compute()")
        ex.message.contains(SameLayerConflictParent.name + "#compute()")
        ex.message.contains(SameLayerConflictInterface.name + "#compute()")
        ex.message.contains("@Sensitive")
        ex.message.contains("@DoNotLog")
    }

    def "ancestor method-level family read returns the same value from the cached MethodNode and the FamilyDetector fallback"() {
        given:
        def subject = declaredMethod(OverrideMaskedChild, "compute")
        def chain = MethodFamilyResolver.analyzeOverrideChain(subject)
        def cacheHitGraph = graphWithDeclaredMethodsFromLayers(chain.layers())
        def cacheMissGraph = graphWithEmptyDeclaredMethods()

        when:
        def viaCache = MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), cacheHitGraph)
        def viaFallback = MethodFamilyResolver.resolveEffectiveMethodFamily(
                subject, Family.NONE, chain.isOverride(), chain.layers(), cacheMissGraph)

        then:
        viaCache == Family.MASK
        viaCache == viaFallback
    }

    def "resolveParameterFamily honours layer-1 parameter-level annotation before method or type level"() {
        given:
        def method = declaredMethod(ParameterAnnotatedHolder, "process", String)
        def graph = graphWithEmptyDeclaredMethods()

        when:
        def family = MethodFamilyResolver.resolveParameterFamily(
                method, 0, Family.NONE, false, List.of(), graph)

        then:
        family == Family.MASK
    }

    def "resolveParameterFamily prefers parameter-level family over method-level family at layer 1"() {
        given:
        def method = declaredMethod(ParameterLevelOverridesMethodLevel, "process", String)
        def graph = graphWithEmptyDeclaredMethods()

        when:
        def family = MethodFamilyResolver.resolveParameterFamily(
                method, 0, Family.NONE, false, List.of(), graph)

        then:
        family == Family.MASK
    }

    def "resolveReturnFamily falls back to declaring type family when method-level annotation is absent on non-override method"() {
        given:
        def method = declaredMethod(NonOverrideSubject, "uniquelyOwn")
        def graph = graphWithEmptyDeclaredMethods()

        when:
        def family = MethodFamilyResolver.resolveReturnFamily(
                method, Family.PASS_THROUGH, false, List.of(), graph)

        then:
        family == Family.PASS_THROUGH
    }

    def "anyParameterCarriesLiteralFamily detects at least one parameter-level family annotation"() {
        given:
        def method = declaredMethod(ParameterAnnotatedHolder, "process", String)

        expect:
        MethodFamilyResolver.anyParameterCarriesLiteralFamily(method)
    }

    def "anyParameterCarriesLiteralFamily returns false when no parameter carries a family annotation"() {
        given:
        def method = declaredMethod(NonOverrideSubject, "uniquelyOwn")

        expect:
        !MethodFamilyResolver.anyParameterCarriesLiteralFamily(method)
    }

    def "anyParameterCarriesLiteralFamily returns false when every parameter lacks a family annotation"() {
        given:
        def method = declaredMethod(MultiParamNoFamily, "process")

        expect:
        !MethodFamilyResolver.anyParameterCarriesLiteralFamily(method)
    }

    private static RegistryRouteGraph graphWithEmptyDeclaredMethods() {
        graphWithFactory({ RegistryRouteGraph graph, TypeDescription type ->
            buildBareNode(type, List.of())
        } as RegistryRouteGraph.TypeNodeFactory)
    }

    private static RegistryRouteGraph graphWithDeclaredMethodsFromLayers(List<List<MethodDescription>> layers) {
        Map<String, List<MethodDescription>> ancestorsByOwner = [:]
        for (List<MethodDescription> layer : layers) {
            for (MethodDescription ancestor : layer) {
                String ownerName = ancestor.getDeclaringType().asErasure().getName()
                ancestorsByOwner.computeIfAbsent(ownerName, { _ -> [] }) << ancestor
            }
        }
        graphWithFactory({ RegistryRouteGraph graph, TypeDescription type ->
            List<MethodDescription> declared = ancestorsByOwner.getOrDefault(type.getName(), [])
            List<MethodNode> methodNodes = declared.collect { md ->
                Family methodLevel = FamilyDetector.detect(
                        md.getDeclaredAnnotations(),
                        md.getDeclaringType().asErasure().getName() + "#" + md.getName() + "()")
                new MethodNode(
                        md.getInternalName(),
                        md.getDescriptor(),
                        false,
                        true,
                        methodLevel,
                        methodLevel,
                        List.of(),
                        Family.NONE,
                        false)
            }
            buildBareNode(type, methodNodes)
        } as RegistryRouteGraph.TypeNodeFactory)
    }

    private static RegistryRouteGraph graphWithFactory(RegistryRouteGraph.TypeNodeFactory factory) {
        def metadata = new RegistryMetadata("test.NoSuchRegistry", 256, List.of())
        new RegistryRouteGraph(metadata, List.of(), List.of(), factory)
    }

    private static TypeNode buildBareNode(TypeDescription type, List<MethodNode> methodNodes) {
        String binaryName = type.getName()
        String packageName = type.getPackage() != null ? type.getPackage().getName() : ""
        String className = packageName.isEmpty() ? binaryName : binaryName.substring(packageName.length() + 1)
        Family typeLevel = FamilyDetector.detect(type.getDeclaredAnnotations(), binaryName)
        new TypeNode(
                packageName,
                className,
                false,
                false,
                typeLevel,
                Optional.empty(),
                Optional.empty(),
                methodNodes,
                List.of(),
                List.of())
    }

    @SuppressWarnings("unused")
    static class StaticHolder {
        static void utility() {}
    }

    @SuppressWarnings("unused")
    static class PrivateHolder {
        private void secret() {}
    }

    @SuppressWarnings("unused")
    static class CtorOnly {
        CtorOnly() {}
    }

    @SuppressWarnings("unused")
    static class NonOverrideSubject {
        void uniquelyOwn() {}
    }

    @SuppressWarnings("unused")
    static class SingleOverrideParent {
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class SingleOverrideChild extends SingleOverrideParent {
        @Override
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class MultiLayerGrandparent {
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class MultiLayerParent extends MultiLayerGrandparent {
        @Override
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class MultiLayerChild extends MultiLayerParent {
        @Override
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class SuppressedAncestor {
        @DoNotLog
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class MethodLevelMaskedOverrideOfSuppressedAncestor extends SuppressedAncestor {
        @Sensitive
        @Override
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class OverrideMaskedParent {
        @Sensitive
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class OverrideMaskedChild extends OverrideMaskedParent {
        @Override
        void compute() {}
    }

    @Sensitive
    @SuppressWarnings("unused")
    static class TypeLevelMaskedParent {
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class TypeLevelMaskedChild extends TypeLevelMaskedParent {
        @Override
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class ParameterAnnotatedHolder {
        void process(@Sensitive String value) {}
    }

    @SuppressWarnings("unused")
    static class ParameterLevelOverridesMethodLevel {
        @DoNotLog
        void process(@Sensitive String value) {}
    }

    @SuppressWarnings("unused")
    static class MultiParamNoFamily {
        void process(String a, int b, Object c) {}
    }

    @SuppressWarnings("unused")
    static class SameLayerSameFamilyParentA {
        @Sensitive
        void compute() {}
    }

    @SuppressWarnings("unused")
    interface SameLayerSameFamilyInterfaceA {
        @Sensitive
        void compute()
    }

    @SuppressWarnings("unused")
    static class SameLayerSameFamilyChild extends SameLayerSameFamilyParentA implements SameLayerSameFamilyInterfaceA {
        @Override
        void compute() {}
    }

    @SuppressWarnings("unused")
    static class SameLayerConflictParent {
        @Sensitive
        void compute() {}
    }

    @SuppressWarnings("unused")
    interface SameLayerConflictInterface {
        @DoNotLog
        void compute()
    }

    @SuppressWarnings("unused")
    static class SameLayerConflictChild extends SameLayerConflictParent implements SameLayerConflictInterface {
        @Override
        void compute() {}
    }
}
