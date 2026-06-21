package org.libprunus.core.error

import spock.lang.Specification

class FallbackErrorCodeSpec extends Specification {

    def "INTERNAL exposes its constant name as the code and maps to the INTERNAL category"() {
        expect:
        FallbackErrorCode.INTERNAL.code() == "INTERNAL"
        FallbackErrorCode.INTERNAL.category() == ErrorCategory.INTERNAL
    }

    def "ships exactly one fallback code so the framework owns no business codes"() {
        expect:
        FallbackErrorCode.values() == [FallbackErrorCode.INTERNAL]
    }
}
