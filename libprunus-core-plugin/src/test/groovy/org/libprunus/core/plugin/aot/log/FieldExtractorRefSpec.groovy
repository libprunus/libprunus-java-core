package org.libprunus.core.plugin.aot.log

import spock.lang.Specification

class FieldExtractorRefSpec extends Specification {

    def "canonical constructor assigns each argument to its declared component position"() {
        expect:
        def ref = new FieldExtractorRef(fieldName, ownerInternalName, methodName, methodDescriptor, isInterface)
        ref.fieldName() == fieldName
        ref.ownerInternalName() == ownerInternalName
        ref.methodName() == methodName
        ref.methodDescriptor() == methodDescriptor
        ref.isInterface() == isInterface

        where:
        fieldName | ownerInternalName  | methodName | methodDescriptor       | isInterface
        "traceId" | "sample/Registry"  | "trace"    | "()Ljava/lang/String;" | false
        "spanId"  | "sample/IRegistry" | "span"     | "()Ljava/lang/String;" | true
    }

    def "equals partitions instances by full 5-tuple of components"() {
        given:
        def base = new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/String;", false)

        expect:
        (base == other) == equalExpected

        where:
        other                                                                                       | equalExpected
        new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/String;", false) | true
        new FieldExtractorRef("Traceid", "sample/Registry", "trace", "()Ljava/lang/String;", false) | false
        new FieldExtractorRef("traceId", "sample/registry", "trace", "()Ljava/lang/String;", false) | false
        new FieldExtractorRef("traceId", "sample/Registry", "Trace", "()Ljava/lang/String;", false) | false
        new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/Object;", false) | false
        new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/String;", true)  | false
    }

    def "hashCode is consistent with equals for fully equal component tuples"() {
        given:
        def base = new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/String;", false)
        def equalCopy = new FieldExtractorRef("traceId", "sample/Registry", "trace", "()Ljava/lang/String;", false)

        expect:
        base == equalCopy
        base.hashCode() == equalCopy.hashCode()
    }
}
