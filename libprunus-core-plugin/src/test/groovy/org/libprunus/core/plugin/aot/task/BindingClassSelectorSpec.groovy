package org.libprunus.core.plugin.aot.task

import org.libprunus.core.plugin.aot.BindingIdSanitizer
import org.libprunus.core.plugin.aot.PrunusPluginConstants
import spock.lang.Specification

class BindingClassSelectorSpec extends Specification {

    def "defaultBindingClassName composes fqcn by joining generated package, sanitized id and impl simple name"() {
        expect:
        BindingClassSelector.defaultBindingClassName(bindingId) ==
                "${PrunusPluginConstants.GENERATED_AOT_PACKAGE}.${BindingIdSanitizer.sanitizeForPackageSegment(bindingId)}.${PrunusPluginConstants.GENERATED_AOT_BINDING_IMPL_SIMPLE_NAME}"

        where:
        bindingId << ["abc123", "  abc123  ", "my-core-module", "123_app", "int"]
    }

    def "defaultBindingClassName returns literal fqcn for representative binding id"() {
        expect:
        BindingClassSelector.defaultBindingClassName("simpleId") ==
                "org.libprunus.aot.generated.simpleId.LogConfigBindingImpl"
    }

    def "select throws project named NPE when defaultBindingClass is null"() {
        when:
        BindingClassSelector.select("a.b.ValidBinding", null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "defaultBindingClass"
    }

    def "select falls back to default binding class when explicit is null blank or whitespace only"() {
        expect:
        !BindingClassSelector.select(explicit, "org.libprunus.aot.generated.x.LogConfigBindingImpl").explicit()
        BindingClassSelector.select(explicit, "org.libprunus.aot.generated.x.LogConfigBindingImpl").bindingClassName() ==
                "org.libprunus.aot.generated.x.LogConfigBindingImpl"

        where:
        explicit << [null, "", "   ", "\t", "\n"]
    }

    def "select rejects explicit binding class that is not a valid Java FQCN"() {
        when:
        BindingClassSelector.select(invalid, "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("not a valid Java FQCN")
        ex.message.contains(invalid.strip())

        where:
        invalid << ["my-module.Binding", "123.Binding", "com..Binding", "com.example.Binding.", "  my-module.Binding  ", "\t123.Binding\n"]
    }

    def "select rejects explicit binding class in reserved namespace"() {
        when:
        BindingClassSelector.select(explicit, "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("reserved package namespace")
        ex.message.contains(explicit.strip())

        where:
        explicit << ["java.lang.CustomBinding", "javax.crypto.BindingImpl", "jdk.internal.Binding", "sun.misc.Binding", "com.sun.proxy.Binding"]
    }

    def "select rejects explicit binding class in reserved namespace regardless of case"() {
        when:
        BindingClassSelector.select(explicit, "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("reserved package namespace")
        ex.message.contains(explicit.strip())

        where:
        explicit << ["Java.lang.X", "JAVAX.crypto.Y", "Jdk.internal.Z", "SUN.misc.W", "Com.Sun.proxy.V", "JAVA.LANG.X"]
    }

    def "select rejects explicit binding class whose segment is a Java reserved keyword"() {
        when:
        BindingClassSelector.select(explicit, "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Java reserved keyword segment")
        ex.message.contains(explicit.strip())

        where:
        explicit << ["int.X", "x.int", "a.true.B", "void.Foo", "x._.Y"]
    }

    def "select chooses explicit binding class when provided"() {
        when:
        def result = BindingClassSelector.select("a.b.CustomBinding", "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        result.bindingClassName() == "a.b.CustomBinding"
        result.explicit()
    }

    def "select strips surrounding whitespace from explicit binding class before validation"() {
        when:
        def result = BindingClassSelector.select("  a.b.CustomBinding  ", "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        result.bindingClassName() == "a.b.CustomBinding"
        result.explicit()
    }

    def "select accepts single segment class name as explicit binding class"() {
        when:
        def result = BindingClassSelector.select("MyBinding", "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        result.bindingClassName() == "MyBinding"
        result.explicit()
    }

    def "select accepts explicit binding class with nested class dollar separator"() {
        when:
        def result = BindingClassSelector.select("com.example.Outer\$Inner", "org.libprunus.aot.generated.x.LogConfigBindingImpl")

        then:
        result.bindingClassName() == "com.example.Outer\$Inner"
        result.explicit()
    }
}
