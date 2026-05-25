package org.libprunus.core.plugin.aot.log

import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.jar.asm.Type
import spock.lang.Specification

class ObjectMethodSignaturesSpec extends Specification {

    def "private constructor throws UnsupportedOperationException and leaves signature table intact"() {
        given:
        def probeName = "toString"
        def probeDescriptor = Type.getMethodDescriptor(Object.class.getDeclaredMethod(probeName))

        when:
        ObjectMethodSignatures.newInstance()

        then:
        thrown(UnsupportedOperationException)
        ObjectMethodSignatures.isDeclaredOnObject(probeName, probeDescriptor)
    }

    def "isDeclaredOnObject returns true for every declared Object instance method derived via reflection"() {
        given:
        def method = Object.class.getDeclaredMethod(name, paramTypes as Class[])
        def descriptor = Type.getMethodDescriptor(method)

        expect:
        ObjectMethodSignatures.isDeclaredOnObject(name, descriptor)

        where:
        name         | paramTypes
        "toString"   | []
        "hashCode"   | []
        "equals"     | [Object]
        "getClass"   | []
        "clone"      | []
        "notify"     | []
        "notifyAll"  | []
        "wait"       | []
        "wait"       | [long]
        "wait"       | [long, int]
        "finalize"   | []
    }

    def "isDeclaredOnObject returns false when name matches an Object method but descriptor does not"() {
        given:
        def realDescriptor = Type.getMethodDescriptor(Object.class.getDeclaredMethod("toString"))

        expect:
        !ObjectMethodSignatures.isDeclaredOnObject("toString", "(I)V")
        ObjectMethodSignatures.isDeclaredOnObject("toString", realDescriptor)
    }

    def "isDeclaredOnObject returns false when descriptor matches an Object method but name is not declared on Object"() {
        given:
        def realDescriptor = Type.getMethodDescriptor(Object.class.getDeclaredMethod("toString"))

        expect:
        !ObjectMethodSignatures.isDeclaredOnObject("display", realDescriptor)
        ObjectMethodSignatures.isDeclaredOnObject("toString", realDescriptor)
    }

    def "isDeclaredOnObject returns false for a typical user-defined business method signature"() {
        expect:
        !ObjectMethodSignatures.isDeclaredOnObject("process", "(Ljava/lang/String;)Ljava/lang/String;")
    }

    def "isDeclaredOnObject returns true for the constructor internal name since the table is keyed by getInternalName"() {
        expect:
        ObjectMethodSignatures.isDeclaredOnObject("<init>", "()V")
    }

    def "isDeclaredOnObject answers true for every method TypeDescription reports for Object including constructor"() {
        given:
        def objectType = TypeDescription.ForLoadedType.of(Object.class)
        def methods = objectType.getDeclaredMethods()

        expect:
        !methods.isEmpty()
        methods.every { ObjectMethodSignatures.isDeclaredOnObject(it.getInternalName(), it.getDescriptor()) }
    }
}
