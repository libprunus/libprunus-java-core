package org.libprunus.core.plugin.aot.log

import java.lang.reflect.Modifier
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool
import org.libprunus.core.log.runtime.LogLevel
import org.libprunus.core.plugin.aot.log.fixture.crosspackage.CrossPackageChild
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.AbstractHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.CtorAndStaticInit
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.FieldFamilyHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.FieldShapeHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.IgnoredClass
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.LabelFieldHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.LiteralParamHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.MaskedClass
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.MethodFamilyHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.MethodIgnoreHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.MethodShapeHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.ObjectOverrider
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.ParamHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.PassThroughClass
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.PlainOuter
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.RootOnlyFields
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.RootWithSuppressedField
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.SamePackageBase
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.SamePackageChild
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.SimpleFieldHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.SuppressedAncestorBase
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.SuppressedAncestorChild
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.SuppressedClass
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.SyntheticInnerHolder
import org.libprunus.core.plugin.aot.log.fixture.typenodebuilder.WithSinglePublicField
import spock.lang.Specification

class TypeNodeBuilderSpec extends Specification {

    private static RegistryRouteGraph graph(String registryBinaryName = "test.NoSuchRegistry",
            List<MethodLoggingRule> methodRules = [],
            List<ToStringRule> toStringRules = []) {
        def metadata = new RegistryMetadata(registryBinaryName, 256, [])
        RegistryRouteGraph.TypeNodeFactory factory =
                { RegistryRouteGraph holder, TypeDescription type -> TypeNodeBuilder.build(holder, type) } as RegistryRouteGraph.TypeNodeFactory
        new RegistryRouteGraph(metadata, methodRules, toStringRules, factory)
    }

    private static MethodLoggingRule methodRule(String routeId, List<String> includePackages,
            List<String> excludePackages, List<String> includeClassSuffixes) {
        new MethodLoggingRule(routeId, includePackages, excludePackages, includeClassSuffixes,
                LogLevel.DEBUG, LogLevel.DEBUG, [])
    }

    private static ToStringRule toStringRule(String routeId, List<String> includePackages,
            List<String> excludePackages, List<String> includeClassSuffixes) {
        new ToStringRule(routeId, includePackages, excludePackages, includeClassSuffixes)
    }

    private static TypeDescription describe(Class<?> owner) {
        def locator = ClassFileLocator.ForClassLoader.of(owner.classLoader)
        def typePool = TypePool.Default.of(locator)
        typePool.describe(owner.name).resolve()
    }

    def "private constructor throws UnsupportedOperationException to enforce non-instantiability"() {
        when:
        new TypeNodeBuilder()

        then:
        thrown(UnsupportedOperationException)
    }

    def "build extracts packageName and className by slicing at the package boundary preserving inner class dollar separator"() {
        given:
        def g = graph()

        when:
        def node = TypeNodeBuilder.build(g, describe(typeUnderTest))

        then:
        node.packageName() == expectedPackageName
        node.className() == expectedClassName

        where:
        typeUnderTest         || expectedPackageName                                              | expectedClassName
        String                || "java.lang"                                                      | "String"
        PlainOuter            || "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"      | "PlainOuter"
        PlainOuter.PlainInner || "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"      | "PlainOuter\$PlainInner"
    }

    def "build flips methodEligible false when binaryName matches metadata registryBinaryName verbatim"() {
        given:
        def g = graph(PlainOuter.name)

        when:
        def node = TypeNodeBuilder.build(g, describe(PlainOuter))

        then:
        !node.methodEligible()
        !node.toStringEligible()
    }

    def "build leaves methodEligible determined by methodRule presence when binaryName differs from metadata registryBinaryName"() {
        given:
        def g = graph("some.Other.Registry")

        when:
        def node = TypeNodeBuilder.build(g, describe(PlainOuter))

        then:
        !node.methodEligible()
        !node.toStringEligible()
    }

    def "build short-circuits methodEligible to false when class carries AutomatedProcessingIgnore annotation"() {
        given:
        def g = graph()

        when:
        def node = TypeNodeBuilder.build(g, describe(IgnoredClass))

        then:
        !node.methodEligible()
        !node.toStringEligible()
    }

    def "build derives typeLevelFamily from FamilyDetector on the class annotations covering NONE MASK SUPPRESS and PASS_THROUGH"() {
        given:
        def g = graph()

        when:
        def node = TypeNodeBuilder.build(g, describe(fixture))

        then:
        node.typeLevelFamily() == expected

        where:
        fixture            || expected
        PlainOuter         || Family.NONE
        MaskedClass        || Family.MASK
        SuppressedClass    || Family.SUPPRESS
        PassThroughClass   || Family.PASS_THROUGH
    }

    def "build assigns empty toStringFieldChain when toStringEligibleOf returns false skipping ancestor walk entirely"() {
        given:
        def g = graph()

        when:
        def node = TypeNodeBuilder.build(g, describe(WithSinglePublicField))

        then:
        !node.toStringEligible()
        node.toStringFieldChain().isEmpty()
    }

    def "build populates toStringFieldChain with root layer slots when toStringEligibleOf returns true"() {
        given:
        def rule = toStringRule("ts-root",
                ["org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"], [], ["WithSinglePublicField"])
        def g = graph("test.NoRegistry", [], [rule])

        when:
        def node = TypeNodeBuilder.build(g, describe(WithSinglePublicField))

        then:
        node.toStringEligible()
        node.toStringFieldChain().size() == 1
        node.toStringFieldChain()[0].name() == "value"
        node.toStringFieldChain()[0].isRootLayer()
    }

    def "matchMethodRule returns empty when no MethodLoggingRule matches the package and class name"() {
        given:
        def g = graph("test.NoRegistry",
                [methodRule("rule-0", ["other.pkg"], [], ["Subject"])],
                [])

        when:
        def result = TypeNodeBuilder."matchMethodRule"(g, "my.pkg", "MyClass", "my.pkg.MyClass")

        then:
        !result.isPresent()
    }

    def "matchMethodRule returns the single matching MethodLoggingRule when exactly one rule matches the package and class name"() {
        given:
        def g = graph("test.NoRegistry",
                [methodRule("rule-0", ["my.pkg"], [], ["MyClass"])],
                [])

        when:
        def result = TypeNodeBuilder."matchMethodRule"(g, "my.pkg", "MyClass", "my.pkg.MyClass")

        then:
        result.isPresent()
        result.get().routeId() == "rule-0"
    }

    def "matchMethodRule throws IllegalStateException whose message carries the class binary name and both conflicting MethodLoggingProfile routeIds when multiple rules match"() {
        given:
        def g = graph("test.NoRegistry",
                [
                    methodRule("rule-0", ["my.pkg"], [], ["MyClass"]),
                    methodRule("rule-1", ["my.pkg"], [], ["MyClass"])
                ],
                [])

        when:
        TypeNodeBuilder."matchMethodRule"(g, "my.pkg", "MyClass", "my.pkg.MyClass")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Method owner route conflict for class my.pkg.MyClass")
        ex.message.contains("multiple MethodLoggingProfile matches")
        ex.message.contains("rule-0")
        ex.message.contains("rule-1")
    }

    def "matchToStringRule returns empty when no ToStringRule matches the package and class name"() {
        given:
        def g = graph("test.NoRegistry", [],
                [toStringRule("ts-0", ["other.pkg"], [], ["Subject"])])

        when:
        def result = TypeNodeBuilder."matchToStringRule"(g, "my.pkg", "MyClass", "my.pkg.MyClass")

        then:
        !result.isPresent()
    }

    def "matchToStringRule returns the single matching ToStringRule when exactly one rule matches the package and class name"() {
        given:
        def g = graph("test.NoRegistry", [],
                [toStringRule("ts-0", ["my.pkg"], [], ["MyClass"])])

        when:
        def result = TypeNodeBuilder."matchToStringRule"(g, "my.pkg", "MyClass", "my.pkg.MyClass")

        then:
        result.isPresent()
        result.get().routeId() == "ts-0"
    }

    def "matchToStringRule throws IllegalStateException whose message carries the ToString owner route conflict prefix and both conflicting ToStringProfile routeIds when multiple rules match"() {
        given:
        def g = graph("test.NoRegistry", [],
                [
                    toStringRule("ts-0", ["my.pkg"], [], ["MyClass"]),
                    toStringRule("ts-1", ["my.pkg"], [], ["MyClass"])
                ])

        when:
        TypeNodeBuilder."matchToStringRule"(g, "my.pkg", "MyClass", "my.pkg.MyClass")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("ToString owner route conflict for class my.pkg.MyClass")
        ex.message.contains("multiple ToStringProfile matches")
        ex.message.contains("ts-0")
        ex.message.contains("ts-1")
    }

    def "buildDeclaredMethods omits constructors and type initializer from MethodNode list while including regular declared methods"() {
        given:
        def g = graph()

        when:
        def nodes = TypeNodeBuilder."buildDeclaredMethods"(g, describe(CtorAndStaticInit), Family.NONE)

        then:
        nodes*.methodName().contains("regular")
        nodes*.methodName().every { !it.startsWith("<") }
    }

    def "buildDeclaredMethods sets MethodNode methodDescriptor and parameterFamilies length matching the declared method shape"() {
        given:
        def g = graph()

        when:
        def nodes = TypeNodeBuilder."buildDeclaredMethods"(g, describe(ParamHolder), Family.NONE)
        def computeNode = nodes.find { it.methodName() == "compute" }

        then:
        computeNode.methodDescriptor() == "(Ljava/lang/String;I)I"
        computeNode.parameterFamilies().size() == 2
        computeNode.parameterFamilies().every { it == Family.NONE }
    }

    def "buildDeclaredMethods sets MethodNode hasMethodLevelIgnore true when method carries AutomatedProcessingIgnore annotation"() {
        given:
        def g = graph()

        when:
        def nodes = TypeNodeBuilder."buildDeclaredMethods"(g, describe(MethodIgnoreHolder), Family.NONE)
        def ignored = nodes.find { it.methodName() == "ignored" }
        def regular = nodes.find { it.methodName() == "regular" }

        then:
        ignored.hasMethodLevelIgnore()
        !regular.hasMethodLevelIgnore()
    }

    def "buildDeclaredMethods derives MethodNode methodLevelFamily from FamilyDetector on method annotations covering NONE MASK SUPPRESS and PASS_THROUGH"() {
        given:
        def g = graph()

        when:
        def nodes = TypeNodeBuilder."buildDeclaredMethods"(g, describe(MethodFamilyHolder), Family.NONE)

        then:
        nodes.find { it.methodName() == "plain" }.methodLevelFamily() == Family.NONE
        nodes.find { it.methodName() == "masked" }.methodLevelFamily() == Family.MASK
        nodes.find { it.methodName() == "suppressed" }.methodLevelFamily() == Family.SUPPRESS
        nodes.find { it.methodName() == "passThrough" }.methodLevelFamily() == Family.PASS_THROUGH
    }

    def "buildDeclaredMethods derives MethodNode anyParameterCarriesLiteralFamily by delegating to MethodFamilyResolver"() {
        given:
        def g = graph()

        when:
        def nodes = TypeNodeBuilder."buildDeclaredMethods"(g, describe(LiteralParamHolder), Family.NONE)
        def withFamily = nodes.find { it.methodName() == "withSensitiveParam" }
        def withoutFamily = nodes.find { it.methodName() == "noFamilyParam" }

        then:
        withFamily.anyParameterCarriesLiteralFamily()
        !withoutFamily.anyParameterCarriesLiteralFamily()
    }

    def "computeShapeEligible rejects static methods"() {
        given:
        def method = describe(MethodShapeHolder).getDeclaredMethods()
                .filter(ElementMatchers.named("staticMethod")).getOnly()

        when:
        def result = TypeNodeBuilder."computeShapeEligible"(method)

        then:
        !result
    }

    def "computeShapeEligible rejects non-public methods covering protected package-private and private visibility"() {
        given:
        def method = describe(MethodShapeHolder).getDeclaredMethods()
                .filter(ElementMatchers.named(name)).getOnly()

        when:
        def result = TypeNodeBuilder."computeShapeEligible"(method)

        then:
        !result

        where:
        name << ["protectedMethod", "packagePrivateMethod", "privateMethod"]
    }

    def "computeShapeEligible rejects abstract methods declared on an abstract class"() {
        given:
        def method = describe(AbstractHolder).getDeclaredMethods()
                .filter(ElementMatchers.named("abstractMethod")).getOnly()

        when:
        def result = TypeNodeBuilder."computeShapeEligible"(method)

        then:
        !result
    }

    def "computeShapeEligible rejects bridge methods generated by generic erasure"() {
        given:
        def methods = describe(String).getDeclaredMethods()
                .filter(ElementMatchers.named("compareTo"))
        def bridge = methods.find { it.isBridge() }

        when:
        def result = TypeNodeBuilder."computeShapeEligible"(bridge)

        then:
        bridge != null
        bridge.isBridge()
        !result
    }

    def "computeShapeEligible rejects methods whose name and descriptor match a method declared on java.lang.Object such as toString hashCode equals"() {
        given:
        def method = describe(ObjectOverrider).getDeclaredMethods()
                .filter(ElementMatchers.named(name)).getOnly()

        when:
        def result = TypeNodeBuilder."computeShapeEligible"(method)

        then:
        !result

        where:
        name << ["toString", "hashCode", "equals"]
    }

    def "computeShapeEligible accepts public instance non-bridge non-Object-signature methods"() {
        given:
        def method = describe(MethodShapeHolder).getDeclaredMethods()
                .filter(ElementMatchers.named("publicInstance")).getOnly()

        when:
        def result = TypeNodeBuilder."computeShapeEligible"(method)

        then:
        result
    }

    def "buildDeclaredFields assigns FieldNode family from field-level annotation when present overriding typeLevelFamily"() {
        when:
        def fields = TypeNodeBuilder."buildDeclaredFields"(describe(FieldFamilyHolder), Family.PASS_THROUGH)

        then:
        fields.find { it.name() == "masked" }.family() == Family.MASK
        fields.find { it.name() == "plain" }.family() == Family.PASS_THROUGH
    }

    def "buildDeclaredFields falls back to typeLevelFamily when field has no family annotation"() {
        when:
        def fields = TypeNodeBuilder."buildDeclaredFields"(describe(SimpleFieldHolder), Family.SUPPRESS)

        then:
        fields.find { it.name() == "x" }.family() == Family.SUPPRESS
    }

    def "buildDeclaredFields defaults to PASS_THROUGH when both field-level and typeLevelFamily are NONE distinguishing field path from method path which defaults to NONE"() {
        when:
        def fields = TypeNodeBuilder."buildDeclaredFields"(describe(SimpleFieldHolder), Family.NONE)

        then:
        fields.find { it.name() == "x" }.family() == Family.PASS_THROUGH
    }

    def "buildDeclaredFields populates FieldNode declaringClassBinaryName name descriptor and accessFlags from FieldDescription verbatim"() {
        when:
        def fields = TypeNodeBuilder."buildDeclaredFields"(describe(LabelFieldHolder), Family.NONE)
        def labelField = fields.find { it.name() == "label" }

        then:
        labelField.declaringClassBinaryName() == LabelFieldHolder.name
        labelField.descriptor() == "Ljava/lang/String;"
        Modifier.isPublic(labelField.accessFlags())
    }

    def "computeFieldShapeEligible rejects static fields"() {
        given:
        def field = describe(FieldShapeHolder).getDeclaredFields()
                .filter(ElementMatchers.named("staticField")).getOnly()

        when:
        def result = TypeNodeBuilder."computeFieldShapeEligible"(field)

        then:
        !result
    }

    def "computeFieldShapeEligible rejects synthetic fields such as the compiler-generated enum values array"() {
        given:
        def field = describe(SyntheticInnerHolder.Inner).getDeclaredFields()
                .filter(ElementMatchers.named("\$VALUES")).getOnly()

        when:
        def result = TypeNodeBuilder."computeFieldShapeEligible"(field)

        then:
        field.isSynthetic()
        !result
    }

    def "computeFieldShapeEligible rejects transient fields"() {
        given:
        def field = describe(FieldShapeHolder).getDeclaredFields()
                .filter(ElementMatchers.named("transientField")).getOnly()

        when:
        def result = TypeNodeBuilder."computeFieldShapeEligible"(field)

        then:
        !result
    }

    def "computeFieldShapeEligible rejects fields whose name starts with dollar sign"() {
        given:
        def field = describe(FieldShapeHolder).getDeclaredFields()
                .filter(ElementMatchers.named("\$dollarField")).getOnly()

        when:
        def result = TypeNodeBuilder."computeFieldShapeEligible"(field)

        then:
        !result
    }

    def "computeFieldShapeEligible accepts public instance non-transient non-dollar-prefix fields"() {
        given:
        def field = describe(FieldShapeHolder).getDeclaredFields()
                .filter(ElementMatchers.named("regular")).getOnly()

        when:
        def result = TypeNodeBuilder."computeFieldShapeEligible"(field)

        then:
        result
    }

    def "buildToStringFieldChain produces root layer FieldRenderSlot whose isRootLayer is true and declaringClassInternalName matches rootType internal name when root declares eligible non-suppress fields"() {
        given:
        def g = graph()
        def rootType = describe(RootOnlyFields)
        def rootPackage = "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"
        def rootFields = TypeNodeBuilder."buildDeclaredFields"(rootType, Family.NONE)

        when:
        def slots = TypeNodeBuilder."buildToStringFieldChain"(
                g, rootType, "RootOnlyFields", rootPackage, rootFields)

        then:
        slots*.name() == ["a", "b"]
        slots.every { it.isRootLayer() }
        slots.every { it.declaringClassInternalName() == rootType.getInternalName() }
    }

    def "buildToStringFieldChain stops ancestor walk at java.lang.Object emitting only root layer slots when root extends Object directly"() {
        given:
        def g = graph()
        def rootType = describe(RootOnlyFields)
        def rootPackage = "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"
        def rootFields = TypeNodeBuilder."buildDeclaredFields"(rootType, Family.NONE)

        when:
        def slots = TypeNodeBuilder."buildToStringFieldChain"(
                g, rootType, "RootOnlyFields", rootPackage, rootFields)

        then:
        slots.size() == 2
        slots*.isRootLayer() == [true, true]
    }

    def "buildToStringFieldChain extends ancestor walk past non-Object superclass emitting both root and ancestor layer slots and labeling ancestor slots with isRootLayer false"() {
        given:
        def g = graph()
        def rootType = describe(SamePackageChild)
        def rootPackage = "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"
        def rootFields = TypeNodeBuilder."buildDeclaredFields"(rootType, Family.NONE)

        when:
        def slots = TypeNodeBuilder."buildToStringFieldChain"(
                g, rootType, "SamePackageChild", rootPackage, rootFields)

        then:
        slots*.name().containsAll(["c", "b"])
        slots.find { it.name() == "c" }.isRootLayer()
        !slots.find { it.name() == "b" }.isRootLayer()
        slots.find { it.name() == "b" }.declaringClassInternalName() == describe(SamePackageBase).getInternalName()
    }

    def "buildToStringFieldChain swallows NoSuchTypeException raised by graph nodeOf during ancestor walk preserving already collected root layer slots without rethrowing"() {
        given:
        def rootType = describe(SamePackageChild)
        def baseType = describe(SamePackageBase)
        def metadata = new RegistryMetadata("test.NoRegistry", 256, [])
        RegistryRouteGraph.TypeNodeFactory throwingFactory = { RegistryRouteGraph holder, TypeDescription type ->
            if (type.getName() == baseType.getName()) {
                throw new TypePool.Resolution.NoSuchTypeException(type.getName())
            }
            TypeNodeBuilder.build(holder, type)
        } as RegistryRouteGraph.TypeNodeFactory
        def g = new RegistryRouteGraph(metadata, [], [], throwingFactory)
        def rootPackage = "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"
        def rootFields = TypeNodeBuilder."buildDeclaredFields"(rootType, Family.NONE)

        when:
        def slots = TypeNodeBuilder."buildToStringFieldChain"(
                g, rootType, "SamePackageChild", rootPackage, rootFields)

        then:
        noExceptionThrown()
        slots*.name() == ["c"]
        slots[0].isRootLayer()
    }

    def "buildToStringFieldChain skips ancestor fields whose family is SUPPRESS while still emitting other eligible ancestor fields"() {
        given:
        def g = graph()
        def rootType = describe(SuppressedAncestorChild)
        def rootPackage = "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"
        def rootFields = TypeNodeBuilder."buildDeclaredFields"(rootType, Family.NONE)

        when:
        def slots = TypeNodeBuilder."buildToStringFieldChain"(
                g, rootType, "SuppressedAncestorChild", rootPackage, rootFields)

        then:
        slots*.name().contains("c")
        slots*.name().contains("kept")
        !slots*.name().contains("suppressed")
    }

    def "buildToStringFieldChain skips root layer fields whose family is SUPPRESS while still emitting other eligible root fields"() {
        given:
        def g = graph()
        def rootType = describe(RootWithSuppressedField)
        def rootPackage = "org.libprunus.core.plugin.aot.log.fixture.typenodebuilder"
        def rootFields = TypeNodeBuilder."buildDeclaredFields"(rootType, Family.NONE)

        when:
        def slots = TypeNodeBuilder."buildToStringFieldChain"(
                g, rootType, "RootWithSuppressedField", rootPackage, rootFields)

        then:
        slots*.name() == ["kept"]
        slots.every { it.isRootLayer() }
    }

    def "buildToStringFieldChain skips package-private ancestor fields whose declaring package differs from root package while still emitting public and protected ancestor fields"() {
        given:
        def g = graph()
        def rootType = describe(CrossPackageChild)
        def rootFields = TypeNodeBuilder."buildDeclaredFields"(rootType, Family.NONE)

        when:
        def slots = TypeNodeBuilder."buildToStringFieldChain"(
                g, rootType, "CrossPackageChild",
                "org.libprunus.core.plugin.aot.log.fixture.crosspackage", rootFields)

        then:
        slots*.name().contains("pub")
        slots*.name().contains("prot")
        !slots*.name().contains("packagePrivate")
        !slots*.name().contains("priv")
    }

    def "isFieldAccessibleFromRoot truth table across public protected private and package-private access modifiers crossed with same-package vs cross-package declaration"() {
        given:
        def field = new FieldNode("decl.Owner", "name", "I", accessFlags, true, Family.NONE)

        when:
        def result = TypeNodeBuilder."isFieldAccessibleFromRoot"(field, declaringPackage, rootPackage)

        then:
        result == expected

        where:
        accessFlags            | declaringPackage | rootPackage || expected
        Modifier.PUBLIC        | "a"              | "b"         || true
        Modifier.PROTECTED     | "a"              | "b"         || true
        Modifier.PRIVATE       | "a"              | "a"         || false
        Modifier.PRIVATE       | "a"              | "b"         || false
        0                      | "a"              | "a"         || true
        0                      | "a"              | "b"         || false
    }

}
