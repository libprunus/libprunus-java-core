package org.libprunus.core.error

import spock.lang.Specification

class ApiErrorExceptionSpec extends Specification {

    def "two-argument constructor carries the error code and safe message and leaves the cause unset"() {
        given:
        def errorCode = FallbackErrorCode.INTERNAL

        when:
        def exception = new ApiErrorException(errorCode, "safe detail")

        then:
        exception.errorCode().is(errorCode)
        exception.message == "safe detail"
        exception.cause == null
    }

    def "three-argument constructor preserves the supplied cause alongside the code and message"() {
        given:
        def errorCode = FallbackErrorCode.INTERNAL
        def cause = new IllegalStateException("internal diagnostics")

        when:
        def exception = new ApiErrorException(errorCode, "safe detail", cause)

        then:
        exception.errorCode().is(errorCode)
        exception.message == "safe detail"
        exception.cause.is(cause)
    }
}
