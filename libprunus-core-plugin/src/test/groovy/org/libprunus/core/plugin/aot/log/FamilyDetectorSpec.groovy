package org.libprunus.core.plugin.aot.log

import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionClassMaskService
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionClassPassThroughService
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionClassSuppressService
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionMultiClassAnnotation
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionMultiFieldAnnotation
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionMultiMethodAnnotation
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionMultiParamAnnotation
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionPlainService
import org.libprunus.core.plugin.aot.log.fixture.sensitive.FamilyResolutionTripleClassAnnotation
import spock.lang.Specification

class FamilyDetectorSpec extends Specification {

    private static final TypePool TYPE_POOL =
            TypePool.Default.of(ClassFileLocator.ForClassLoader.of(FamilyDetectorSpec.classLoader))

    def "private constructor blocks reflective instantiation"() {
        when:
        new FamilyDetector()

        then:
        thrown(UnsupportedOperationException)
    }

    def "detect returns NONE when annotations argument is null without inspecting target descriptor"() {
        when:
        def result = FamilyDetector.detect(null, "any.target")

        then:
        result == Family.NONE
        noExceptionThrown()
    }

    def "detect returns NONE when annotation list contains zero family annotations"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionPlainService.name).resolve()

        when:
        def result = FamilyDetector.detect(type.getDeclaredAnnotations(), type.name)

        then:
        result == Family.NONE
        noExceptionThrown()
    }

    def "detect returns MASK when annotation list carries only Sensitive"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionClassMaskService.name).resolve()

        when:
        def result = FamilyDetector.detect(type.getDeclaredAnnotations(), type.name)

        then:
        result == Family.MASK
    }

    def "detect returns SUPPRESS when annotation list carries only DoNotLog"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionClassSuppressService.name).resolve()

        when:
        def result = FamilyDetector.detect(type.getDeclaredAnnotations(), type.name)

        then:
        result == Family.SUPPRESS
    }

    def "detect returns PASS_THROUGH when annotation list carries only DoLog"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionClassPassThroughService.name).resolve()

        when:
        def result = FamilyDetector.detect(type.getDeclaredAnnotations(), type.name)

        then:
        result == Family.PASS_THROUGH
    }

    def "detect throws IllegalStateException with mutually-exclusive message when annotation list carries Sensitive and DoNotLog on the same class target"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionMultiClassAnnotation.name).resolve()

        when:
        FamilyDetector.detect(type.getDeclaredAnnotations(), type.name)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("@Sensitive / @DoNotLog / @DoLog are mutually exclusive on ")
        ex.message.contains(type.name)
        ex.message.contains("Sensitive")
        ex.message.contains("DoNotLog")
    }

    def "detect throws IllegalStateException when annotation list carries Sensitive DoNotLog and DoLog on the same class target"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionTripleClassAnnotation.name).resolve()

        when:
        FamilyDetector.detect(type.getDeclaredAnnotations(), type.name)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("mutually exclusive")
    }

    def "detect throws IllegalStateException when annotation list carries Sensitive and DoLog at method level"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionMultiMethodAnnotation.name).resolve()
        def method = type.getDeclaredMethods().filter(ElementMatchers.named("conflict")).getOnly()
        def descriptor = type.name + "#" + method.name + "()"

        when:
        FamilyDetector.detect(method.getDeclaredAnnotations(), descriptor)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("mutually exclusive on " + descriptor)
    }

    def "detect throws IllegalStateException when annotation list carries Sensitive and DoNotLog at field level"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionMultiFieldAnnotation.name).resolve()
        def field = type.getDeclaredFields().filter(ElementMatchers.named("tainted")).getOnly()
        def descriptor = type.name + "#" + field.name

        when:
        FamilyDetector.detect(field.getDeclaredAnnotations(), descriptor)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("mutually exclusive on " + descriptor)
    }

    def "detect throws IllegalStateException when annotation list carries Sensitive and DoNotLog at parameter level"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionMultiParamAnnotation.name).resolve()
        def method = type.getDeclaredMethods().filter(ElementMatchers.named("collide")).getOnly()
        def param = method.getParameters().get(2)
        def descriptor = type.name + "#" + method.name + "() param[2]"

        when:
        FamilyDetector.detect(param.getDeclaredAnnotations(), descriptor)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("mutually exclusive on " + descriptor)
    }

    def "hasAnyFamily returns false when annotations argument is null"() {
        when:
        def result = FamilyDetector.hasAnyFamily(null)

        then:
        result == false
        noExceptionThrown()
    }

    def "hasAnyFamily returns false when annotation list contains zero family annotations"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionPlainService.name).resolve()

        when:
        def result = FamilyDetector.hasAnyFamily(type.getDeclaredAnnotations())

        then:
        result == false
    }

    def "hasAnyFamily returns true when annotation list carries a single family annotation"() {
        given:
        def type = TYPE_POOL.describe(fixtureType.name).resolve()

        when:
        def result = FamilyDetector.hasAnyFamily(type.getDeclaredAnnotations())

        then:
        result == true

        where:
        fixtureType << [
                FamilyResolutionClassMaskService,
                FamilyResolutionClassSuppressService,
                FamilyResolutionClassPassThroughService
        ]
    }

    def "hasAnyFamily returns true without throwing when annotation list carries multiple mutually exclusive family annotations"() {
        given:
        def type = TYPE_POOL.describe(FamilyResolutionMultiClassAnnotation.name).resolve()

        when:
        def result = FamilyDetector.hasAnyFamily(type.getDeclaredAnnotations())

        then:
        result == true
        noExceptionThrown()
    }
}
