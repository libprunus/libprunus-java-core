package org.libprunus.core.plugin.aot.log

import java.util.Optional
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.matcher.ElementMatchers
import spock.lang.Specification

class RegistryRouteGraphSpec extends Specification {

    private static final TypeDescription SUBJECT_TYPE = TypeDescription.ForLoadedType.of(SampleSubject)
    private static final TypeDescription SECONDARY_TYPE = TypeDescription.ForLoadedType.of(SecondarySubject)

    private static MethodDescription subjectMethod(String name) {
        SUBJECT_TYPE.getDeclaredMethods()
                .filter(ElementMatchers.named(name))
                .getOnly()
    }

    private static MethodNode plainMethodNode(MethodDescription method, boolean shapeEligible = true,
            boolean methodLevelIgnore = false, Family effective = Family.NONE,
            boolean anyParamLiteralFamily = false) {
        new MethodNode(
                method.getInternalName(),
                method.getDescriptor(),
                methodLevelIgnore,
                shapeEligible,
                Family.NONE,
                effective,
                List.of(),
                Family.NONE,
                anyParamLiteralFamily)
    }

    def "metadata returns the same RegistryMetadata instance supplied to the constructor"() {
        given:
        def metadata = new RegistryMetadata("custom.RegistryAnchor", 1024, ["alpha.A", "beta.B"])
        def graph = new RegistryRouteGraph(metadata, List.of(), List.of(),
                { RegistryRouteGraph g, TypeDescription type -> bareNode(type, []) } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        graph.metadata().is(metadata)
    }

    def "globalMaxMessageLength delegates to the metadata maxMessageLength component verbatim"() {
        given:
        def metadata = new RegistryMetadata("test.Registry", 4096, List.of())
        def graph = new RegistryRouteGraph(metadata, List.of(), List.of(),
                { RegistryRouteGraph g, TypeDescription type -> bareNode(type, []) } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        graph.globalMaxMessageLength() == 4096
    }

    def "directToStringWhitelist delegates to the metadata directToStringWhitelist component preserving order"() {
        given:
        def whitelist = ["x.y.First", "a.b.Second", "m.n.Third"]
        def metadata = new RegistryMetadata("test.Registry", 256, whitelist)
        def graph = new RegistryRouteGraph(metadata, List.of(), List.of(),
                { RegistryRouteGraph g, TypeDescription type -> bareNode(type, []) } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        graph.directToStringWhitelist() == ["x.y.First", "a.b.Second", "m.n.Third"]
    }

    def "methodLoggingRules and toStringRules snapshot the constructor input so later source mutation does not leak in and the returned lists reject mutation"() {
        given:
        def sourceMethodRules = new ArrayList<MethodLoggingRule>()
        sourceMethodRules.add(new MethodLoggingRule("m1", ["p"], [], ["S"],
                org.libprunus.core.log.runtime.LogLevel.DEBUG,
                org.libprunus.core.log.runtime.LogLevel.DEBUG,
                []))
        def sourceToStringRules = new ArrayList<ToStringRule>()
        sourceToStringRules.add(new ToStringRule("t1", ["p"], [], ["S"]))
        def metadata = new RegistryMetadata("test.Registry", 256, List.of())
        def graph = new RegistryRouteGraph(metadata, sourceMethodRules, sourceToStringRules,
                { RegistryRouteGraph g, TypeDescription type -> bareNode(type, []) } as RegistryRouteGraph.TypeNodeFactory)

        when:
        sourceMethodRules.add(new MethodLoggingRule("m2", ["q"], [], ["S"],
                org.libprunus.core.log.runtime.LogLevel.DEBUG,
                org.libprunus.core.log.runtime.LogLevel.DEBUG,
                []))
        sourceToStringRules.add(new ToStringRule("t2", ["q"], [], ["S"]))

        then:
        graph.methodLoggingRules().size() == 1
        graph.methodLoggingRules()[0].routeId() == "m1"
        graph.toStringRules().size() == 1
        graph.toStringRules()[0].routeId() == "t1"

        when:
        graph.methodLoggingRules().add(new MethodLoggingRule("rogue", ["r"], [], ["S"],
                org.libprunus.core.log.runtime.LogLevel.DEBUG,
                org.libprunus.core.log.runtime.LogLevel.DEBUG,
                []))

        then:
        thrown(UnsupportedOperationException)

        when:
        graph.toStringRules().add(new ToStringRule("rogue", ["r"], [], ["S"]))

        then:
        thrown(UnsupportedOperationException)
    }

    def "nodeOf builds and caches a TypeNode via factory on first call; subsequent calls return same instance"() {
        given:
        def buildCount = new int[1]
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            buildCount[0]++
            bareNode(type, [])
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        def first = graph.nodeOf(SUBJECT_TYPE)
        def second = graph.nodeOf(SUBJECT_TYPE)

        then:
        first.is(second)
        buildCount[0] == 1
    }

    def "nodeOf returns the pre-seeded TypeNode when the cache already holds an entry for the requested type"() {
        given:
        def buildCount = new int[1]
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            buildCount[0]++
            bareNode(type, [])
        } as RegistryRouteGraph.TypeNodeFactory)
        def preSeeded = bareNode(SUBJECT_TYPE, [])
        graph.nodeCache.put(SUBJECT_TYPE.getName(), preSeeded)

        when:
        def result = graph.nodeOf(SUBJECT_TYPE)

        then:
        result.is(preSeeded)
        buildCount[0] == 0
    }

    def "nodeOf is reentrant from within a factory build path without deadlocking on the underlying cache"() {
        // WHY: locks the production WHY comment at RegistryRouteGraph.java:50-51 - build recurses
        // through supertype chains via nodeOf, so the cache must use putIfAbsent rather than
        // computeIfAbsent which rejects same-key reentrant updates.
        given:
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            if (type.getName() == SUBJECT_TYPE.getName()) {
                g.nodeOf(SECONDARY_TYPE)
            }
            bareNode(type, [])
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        def result = graph.nodeOf(SUBJECT_TYPE)

        then:
        result != null
        graph.nodeCache.containsKey(SUBJECT_TYPE.getName())
        graph.nodeCache.containsKey(SECONDARY_TYPE.getName())
    }

    def "classNameOf and declaringClassSimpleNameFor both resolve to the TypeNode className via the same dispatch path"() {
        given:
        def buildCount = new int[1]
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            buildCount[0]++
            bareNode(type, [])
        } as RegistryRouteGraph.TypeNodeFactory)
        def method = subjectMethod("doWork")

        when:
        def directName = graph.classNameOf(SUBJECT_TYPE)
        def methodViewName = graph.declaringClassSimpleNameFor(method)

        then:
        directName == "RegistryRouteGraphSpec\$SampleSubject"
        methodViewName == directName
        buildCount[0] == 1
    }

    def "isRouteRelevant truth table covers method-only, toString-only, both, and neither rule presence combinations"() {
        given:
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            nodeWithRules(type, methodPresent, toStringPresent)
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        graph.isRouteRelevant(SUBJECT_TYPE) == expected

        where:
        methodPresent | toStringPresent || expected
        true          | false           || true
        false         | true            || true
        true          | true            || true
        false         | false           || false
    }

    def "methodEligible and toStringEligible each project the matching TypeNode predicate independently across the four (method, toString) presence combinations"() {
        given:
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            nodeWithRules(type, methodPresent, toStringPresent)
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        graph.methodEligible(SUBJECT_TYPE) == methodPresent
        graph.toStringEligible(SUBJECT_TYPE) == toStringPresent

        where:
        methodPresent | toStringPresent
        true          | false
        false         | true
        true          | true
        false         | false
    }

    def "methodRuleFor returns the MethodLoggingRule held by the cached TypeNode when methodRule is present"() {
        given:
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            nodeWithRules(type, true, false)
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        def rule = graph.methodRuleFor(SUBJECT_TYPE)

        then:
        rule != null
        rule.routeId() == "r"
    }

    def "methodRuleFor fails fast with IllegalStateException whose message carries the contract Method-rule-missing keyword plus the type binary FQCN anchor"() {
        given:
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            nodeWithRules(type, false, false)
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        graph.methodRuleFor(SUBJECT_TYPE)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Method rule missing for")
        ex.message.contains(SUBJECT_TYPE.name)
    }

    def "toStringFieldChain returns the FieldRenderSlot list held by the cached TypeNode by reference"() {
        given:
        def chain = [new FieldRenderSlot("Subject", "Subject", "f", "Ljava/lang/String;", 0, Family.NONE, true)]
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            new TypeNode(
                    "",
                    type.getName(),
                    false,
                    false,
                    Family.NONE,
                    Optional.empty(),
                    Optional.empty(),
                    [],
                    [],
                    chain)
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        def result = graph.toStringFieldChain(SUBJECT_TYPE)

        then:
        result.size() == 1
        result[0].is(chain[0])
    }

    def "shouldEmitEnterExitFor returns false when the supplied MethodNode reference is null"() {
        given:
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [])
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        !graph.shouldEmitEnterExitFor(null)
    }

    def "shouldEmitEnterExitFor returns false when the method is shape-ineligible"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method, false)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        !graph.shouldEmitEnterExitFor(methodNode)
    }

    def "shouldEmitEnterExitFor returns false when the method carries a method-level ignore marker"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method, true, true)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        !graph.shouldEmitEnterExitFor(methodNode)
    }

    def "shouldEmitEnterExitFor returns false when whole-method skip applies (SUPPRESS with no parameter literal family)"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method, true, false, Family.SUPPRESS, false)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        !graph.shouldEmitEnterExitFor(methodNode)
    }

    def "shouldEmitEnterExitFor returns true when whole-method skip is blocked by a parameter literal family even though effective family is SUPPRESS"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method, true, false, Family.SUPPRESS, true)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        graph.shouldEmitEnterExitFor(methodNode)
    }

    def "shouldEmitEnterExitFor returns true for an ordinary eligible method with no ignore marker and no whole-method skip"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        expect:
        graph.shouldEmitEnterExitFor(methodNode)
    }

    def "findDeclaredMethodNode returns the same MethodNode instance held in the cached TypeNode index"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        def result = graph.findDeclaredMethodNode(SUBJECT_TYPE, method.getInternalName(), method.getDescriptor())

        then:
        result.is(methodNode)
    }

    def "findDeclaredMethodNode returns null when descriptor does not match any declared method on the node"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        def result = graph.findDeclaredMethodNode(SUBJECT_TYPE, method.getInternalName(), "(I)V")

        then:
        result == null
    }

    def "requireMethodNode returns the MethodNode when present in the cached TypeNode index"() {
        given:
        def method = subjectMethod("doWork")
        def methodNode = plainMethodNode(method)
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [methodNode])
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        def result = graph.requireMethodNode(method)

        then:
        result.is(methodNode)
    }

    def "requireMethodNode throws IllegalStateException whose message carries the contract Method-node-missing keyword plus the declaring binary FQCN, internal method name, and JVM descriptor anchors"() {
        given:
        def method = subjectMethod("doWork")
        def graph = graphFor({ RegistryRouteGraph g, TypeDescription type ->
            bareNode(type, [])
        } as RegistryRouteGraph.TypeNodeFactory)

        when:
        graph.requireMethodNode(method)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Method node missing")
        ex.message.contains(SampleSubject.getName())
        ex.message.contains("doWork")
        ex.message.contains(method.getDescriptor())
    }

    private static RegistryRouteGraph graphFor(RegistryRouteGraph.TypeNodeFactory factory) {
        def metadata = new RegistryMetadata("test.NoSuchRegistry", 256, List.of())
        new RegistryRouteGraph(metadata, List.of(), List.of(), factory)
    }

    private static TypeNode bareNode(TypeDescription type, List<MethodNode> methodNodes) {
        String binaryName = type.getName()
        String packageName = type.getPackage() != null ? type.getPackage().getName() : ""
        String className = packageName.isEmpty() ? binaryName : binaryName.substring(packageName.length() + 1)
        new TypeNode(
                packageName,
                className,
                false,
                false,
                Family.NONE,
                Optional.empty(),
                Optional.empty(),
                methodNodes,
                List.of(),
                List.of())
    }

    private static TypeNode nodeWithRules(TypeDescription type, boolean methodRulePresent, boolean toStringRulePresent) {
        String binaryName = type.getName()
        String packageName = type.getPackage() != null ? type.getPackage().getName() : ""
        String className = packageName.isEmpty() ? binaryName : binaryName.substring(packageName.length() + 1)
        Optional<MethodLoggingRule> methodRule = methodRulePresent
                ? Optional.of(new MethodLoggingRule("r", [packageName], [], [className],
                        org.libprunus.core.log.runtime.LogLevel.DEBUG,
                        org.libprunus.core.log.runtime.LogLevel.DEBUG,
                        []))
                : Optional.empty()
        Optional<ToStringRule> toStringRule = toStringRulePresent
                ? Optional.of(new ToStringRule("ts", [packageName], [], [className]))
                : Optional.empty()
        new TypeNode(
                packageName,
                className,
                false,
                false,
                Family.NONE,
                methodRule,
                toStringRule,
                [],
                [],
                [])
    }

    @SuppressWarnings("unused")
    static class SampleSubject {
        void doWork() {}
    }

    @SuppressWarnings("unused")
    static class SecondarySubject {
    }
}
