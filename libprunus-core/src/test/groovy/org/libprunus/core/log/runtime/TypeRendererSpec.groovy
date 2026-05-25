package org.libprunus.core.log.runtime

import java.lang.reflect.Modifier
import spock.lang.Specification

class TypeRendererSpec extends Specification {

    def "TypeRenderer is a capture-free SAM whose render(StringBuilderWithContext, Object):void shape is required by the inline lambda cache"() {
        given:
        def declaredMethods = TypeRenderer.getDeclaredMethods()
        def abstractMethods = declaredMethods.findAll { Modifier.isAbstract(it.modifiers) }
        def render = abstractMethods.first()

        expect: "exactly one abstract method anchors the SAM"
        abstractMethods.size() == 1

        and: "no default method may sneak in and split the SAM"
        !declaredMethods.any { it.isDefault() }

        and: "no static method may contribute to the interface surface"
        declaredMethods.findAll { Modifier.isStatic(it.modifiers) }.isEmpty()

        and: "the SAM is named render"
        render.name == "render"

        and: "render returns void so dispatch results are produced only via StringBuilderWithContext side effects"
        render.returnType == void.class

        and: "render parameter list locks the (StringBuilderWithContext, Object) shape assumed by verifyNoCaptureRendererCandidates"
        render.parameterTypes == [StringBuilderWithContext, Object] as Class[]

        and: "zero fields so concrete lambdas remain capture-free"
        TypeRenderer.getDeclaredFields().length == 0
    }

    def "TypeRenderer accepts a lambda assigned to NonSealedTypeRenderer and routes its render call to the supplied StringBuilderWithContext"() {
        given:
        def captured = new ArrayList<Object>()
        NonSealedTypeRenderer renderer = (c, v) -> { captured.add([c, v]) }
        def sbwc = new StringBuilderWithContext(new StringBuilder())
        def payload = new Object()

        when:
        renderer.render(sbwc, payload)

        then: "lambda was invoked exactly once with the supplied context and value transparently routed through"
        captured.size() == 1
        captured[0][0].is(sbwc)
        captured[0][1].is(payload)

        and: "builder remains unmutated by the no-op lambda body — proves the interface dispatch did not leak side effects"
        sbwc.builder.length() == 0
    }

    def "TypeRenderer is exactly package-private — not public, not protected, not private — so only same-package permitted subclasses can implement it"() {
        given:
        def modifiers = TypeRenderer.modifiers

        expect: "TypeRenderer is not public — external modules cannot inject custom dispatch implementations"
        !Modifier.isPublic(modifiers)

        and: "TypeRenderer is not protected — closes the JLS-allowed nested-interface access channel"
        !Modifier.isProtected(modifiers)

        and: "TypeRenderer is not private — it must remain package-visible so the renderer cache can populate from peers in the same package"
        !Modifier.isPrivate(modifiers)
    }

    def "TypeRenderer is a sealed interface permitting exactly the five renderer types plus NonSealedTypeRenderer"() {
        expect:
        TypeRenderer.isSealed()

        and: "permits list size is independently asserted so adding a new permits subclass surfaces directly"
        TypeRenderer.permittedSubclasses.length == 6

        and:
        TypeRenderer.permittedSubclasses as Set == [
            IdentityRenderer, LoggableRenderer, ObjectArrayRenderer,
            CollectionRenderer, MapRenderer, NonSealedTypeRenderer
        ] as Set
    }

    def "NonSealedTypeRenderer is a non-sealed extension of TypeRenderer so lambda dispatch entries remain expressible"() {
        expect:
        NonSealedTypeRenderer.isInterface()
        !NonSealedTypeRenderer.isSealed()
        TypeRenderer.isAssignableFrom(NonSealedTypeRenderer)
    }
}
