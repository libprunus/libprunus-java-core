package org.libprunus.core.log.runtime

import java.lang.reflect.Modifier
import spock.lang.Specification

class LoggableSpec extends Specification {

    def "interface fully qualified name stays in sync with the AOT byte-code rewrite literal"() {
        expect:
        Loggable.name == "org.libprunus.core.log.runtime.Loggable"
    }

    def "single abstract method name matches the AOT generated method literal"() {
        given: "all abstract methods declared on the interface"
        def abstractMethods = Loggable.declaredMethods.findAll {
            Modifier.isAbstract(it.modifiers)
        }

        expect: "there is exactly one abstract method"
        abstractMethods.size() == 1

        and: "its name is the literal that AotLogConstants.AOT_RENDER_METHOD pins"
        abstractMethods[0].name == "_libprunus_render"
    }

    def "SAM descriptor matches the AOT-pinned (LStringBuilderWithContext;)V shape"() {
        given:
        def method = Loggable.getDeclaredMethod("_libprunus_render", StringBuilderWithContext)

        expect:
        method.returnType == void.class

        and:
        method.parameterCount == 1

        and:
        method.parameterTypes == [StringBuilderWithContext] as Class[]
    }

    def "single abstract method declares no checked exceptions so AOT-generated callsites do not need ATHROW wrappers"() {
        given:
        def method = Loggable.getDeclaredMethod("_libprunus_render", StringBuilderWithContext)

        expect:
        method.exceptionTypes.length == 0
    }

    def "interface declares no default or static methods so the AOT-generated SAM shape is preserved"() {
        given: "all directly declared methods on the interface"
        def declared = Loggable.declaredMethods
        def defaultCount = declared.count { it.isDefault() }
        def staticCount = declared.count { Modifier.isStatic(it.modifiers) }

        expect: "no default methods exist — preserves the SAM shape AOT-generated classes implement"
        defaultCount == 0

        and: "no static methods exist — preserves the SAM shape"
        staticCount == 0
    }

    def "interface declares no fields so AOT-generated implementations inherit only the SAM contract"() {
        expect:
        Loggable.declaredFields.length == 0
    }

    def "interface declares no super-interfaces so the AOT-generated SAM hierarchy stays flat"() {
        expect:
        Loggable.interfaces.length == 0
    }

    def "type stays a public non-sealed interface so user code can implement it"() {
        expect: "Loggable is an interface — AOT generation implements it from arbitrary user classes"
        Loggable.isInterface()

        and: "Loggable is public — user POJOs in any package can implement it"
        Modifier.isPublic(Loggable.modifiers)

        and: "Loggable is not sealed — AOT plugin must be free to generate implementing subclasses from any user type"
        !Loggable.isSealed()
    }

    def "any concrete subtype of Loggable resolves to LoggableRenderer through StringBuilderWithContext's type dispatch table"() {
        given: "an anonymous Loggable subtype — the broadest shape of user-side implementations the AOT-rewritten pipeline produces"
        def loggableImpl = new Loggable() {
            @Override
            void _libprunus_render(StringBuilderWithContext ctx) {
                ctx.append("anon-loggable")
            }
        }

        when: "the (package-private static) resolveRenderer is invoked with the Loggable subtype — Groovy's dynamic dispatch reaches the production routing decision directly"
        def renderer = StringBuilderWithContext.resolveRenderer(loggableImpl.class)

        then: "the dispatch table routes Loggable subtypes to the LoggableRenderer.INSTANCE singleton"
        renderer.is(LoggableRenderer.INSTANCE)

        and: "the route is not the identity fallback — proving the Loggable predicate fired before the general fallback branch"
        !renderer.is(IdentityRenderer.INSTANCE)
    }
}
