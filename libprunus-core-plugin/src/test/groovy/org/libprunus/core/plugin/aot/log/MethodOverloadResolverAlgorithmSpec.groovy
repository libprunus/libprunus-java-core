package org.libprunus.core.plugin.aot.log

import java.util.Optional
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.matcher.ElementMatchers
import spock.lang.Specification

class MethodOverloadResolverAlgorithmSpec extends Specification {

    private static MethodDescription declared(Class<?> owner, String name, Class<?>... paramTypes) {
        TypeDescription.ForLoadedType.of(owner)
                .getDeclaredMethods()
                .filter(ElementMatchers.named(name).and(ElementMatchers.takesArguments(paramTypes)))
                .getOnly()
    }

    private static MethodNode methodNode(String binaryName, String name, String descriptor,
            boolean shapeEligible = true, boolean methodLevelIgnore = false) {
        new MethodNode(
                name, descriptor,
                methodLevelIgnore, shapeEligible,
                Family.NONE, Family.NONE, List.of(), Family.NONE, false)
    }

    def "detectOverloadedNames returns an empty set when the cached type node declares no methods"() {
        given:
        def graph = graphWithNodes("sample.Subject", [])

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == [] as Set
    }

    def "detectOverloadedNames returns an empty set when distinct method names appear only once each"() {
        given:
        def nodes = [
            methodNode("sample.Subject", "alpha", "()V"),
            methodNode("sample.Subject", "beta", "()V")
        ]
        def graph = graphWithNodes("sample.Subject", nodes)

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == [] as Set
    }

    def "detectOverloadedNames returns the name of every method appearing in more than one eligible overload"() {
        given:
        def nodes = [
            methodNode("sample.Subject", "alpha", "()V"),
            methodNode("sample.Subject", "alpha", "(I)V"),
            methodNode("sample.Subject", "beta", "()V")
        ]
        def graph = graphWithNodes("sample.Subject", nodes)

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == ["alpha"] as Set
    }

    def "detectOverloadedNames returns multiple names when more than one method group has overloads"() {
        given:
        def nodes = [
            methodNode("sample.Subject", "alpha", "()V"),
            methodNode("sample.Subject", "alpha", "(I)V"),
            methodNode("sample.Subject", "beta", "()V"),
            methodNode("sample.Subject", "beta", "(Ljava/lang/String;)V")
        ]
        def graph = graphWithNodes("sample.Subject", nodes)

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == ["alpha", "beta"] as Set
    }

    def "detectOverloadedNames skips methods that fail shape eligibility so ineligible duplicates do not count as overloads"() {
        given:
        def nodes = [
            methodNode("sample.Subject", "alpha", "()V", false),
            methodNode("sample.Subject", "alpha", "(I)V", false),
            methodNode("sample.Subject", "alpha", "(J)V", true)
        ]
        def graph = graphWithNodes("sample.Subject", nodes)

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == [] as Set
    }

    def "detectOverloadedNames skips methods carrying a method-level ignore marker so ignored duplicates do not count as overloads"() {
        given:
        def nodes = [
            methodNode("sample.Subject", "alpha", "()V", true, true),
            methodNode("sample.Subject", "alpha", "(I)V", true, false)
        ]
        def graph = graphWithNodes("sample.Subject", nodes)

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == [] as Set
    }

    def "detectOverloadedNames does not let skipped same-named methods promote a lone eligible sibling to overloaded"() {
        given:
        def nodes = [
            methodNode("sample.Subject", "alpha", "()V", true, true),
            methodNode("sample.Subject", "alpha", "(I)V", false, false),
            methodNode("sample.Subject", "alpha", "(J)V", true, false)
        ]
        def graph = graphWithNodes("sample.Subject", nodes)

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == [] as Set
    }

    def "detectOverloadedNames returns an empty set when every declared method is skipped by shape or ignore filters"() {
        given:
        def nodes = [
            methodNode("sample.Subject", "alpha", "()V", false, false),
            methodNode("sample.Subject", "alpha", "(I)V", true, true)
        ]
        def graph = graphWithNodes("sample.Subject", nodes)

        when:
        def result = MethodOverloadResolver.detectOverloadedNames(graph, TypeDescription.ForLoadedType.of(SampleSubject))

        then:
        result == [] as Set
    }

    def "buildOverloadSuffix returns the void-marker plus return type for a no-parameter method"() {
        given:
        def method = declared(SampleSubject, "noArgs")

        when:
        def suffix = MethodOverloadResolver.buildOverloadSuffix(method)

        then:
        suffix == '$void$void'
    }

    def "buildOverloadSuffix preserves a primitive return type unchanged in the suffix"() {
        given:
        def method = declared(SampleSubject, "returnsInt")

        when:
        def suffix = MethodOverloadResolver.buildOverloadSuffix(method)

        then:
        suffix == '$void$int'
    }

    def "buildOverloadSuffix appends a sanitized parameter and return type for a single-parameter method"() {
        given:
        def method = declared(SampleSubject, "single", String)

        when:
        def suffix = MethodOverloadResolver.buildOverloadSuffix(method)

        then:
        suffix == '$java_lang_String$java_lang_String'
    }

    def "buildOverloadSuffix preserves the parameter order in the suffix for multi-parameter primitive plus reference combinations"() {
        given:
        def method = declared(SampleSubject, "mixed", int, String)

        when:
        def suffix = MethodOverloadResolver.buildOverloadSuffix(method)

        then:
        suffix == '$int$java_lang_String$void'
    }

    def "buildOverloadSuffix sanitizes array marker characters in parameter and return types"() {
        given:
        def method = declared(SampleSubject, "arrayParam", String[].class)

        when:
        def suffix = MethodOverloadResolver.buildOverloadSuffix(method)

        then:
        suffix == '$_Ljava_lang_String_$_Ljava_lang_String_'
    }

    def "buildOverloadSuffix sanitizes the dollar sign character inside an inner class binary name"() {
        given:
        def method = declared(SampleSubject, "innerParam", SampleSubject.Inner)

        when:
        def suffix = MethodOverloadResolver.buildOverloadSuffix(method)

        then:
        suffix.contains('SampleSubject_Inner')
        !suffix.contains('SampleSubject$Inner')
    }

    private static RegistryRouteGraph graphWithNodes(String binaryName, List<MethodNode> methodNodes) {
        def metadata = new RegistryMetadata("test.NoSuchRegistry", 256, List.of())
        RegistryRouteGraph.TypeNodeFactory factory = { RegistryRouteGraph graph, TypeDescription type ->
            String pkg = type.getPackage() != null ? type.getPackage().getName() : ""
            String simple = pkg.isEmpty() ? type.getName() : type.getName().substring(pkg.length() + 1)
            new TypeNode(
                    pkg,
                    simple,
                    false,
                    false,
                    Family.NONE,
                    Optional.empty(),
                    Optional.empty(),
                    methodNodes,
                    List.of(),
                    List.of())
        }
        new RegistryRouteGraph(metadata, List.of(), List.of(), factory)
    }

    @SuppressWarnings("unused")
    static class SampleSubject {
        void noArgs() {}

        int returnsInt() { 0 }

        String single(String s) { s }

        void mixed(int a, String b) {}

        String[] arrayParam(String[] arr) { arr }

        void innerParam(Inner inner) {}

        static class Inner {}
    }
}
