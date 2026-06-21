package org.libprunus.spring.error

import org.libprunus.core.error.ErrorCategory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.ErrorResponseException
import org.springframework.web.context.request.ServletWebRequest
import spock.lang.Specification

class ApiErrorHandlerSpec extends Specification {

    private final ApiErrorHandler handler = new ApiErrorHandler()
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest())

    def "a built-in exception whose ProblemDetail the framework materializes receives a status-derived code"() {
        when: "the framework path: body is null and materialized inside handleExceptionInternal"
        def response = handler.handleExceptionInternal(
                new ErrorResponseException(HttpStatus.NOT_FOUND), null, new HttpHeaders(), HttpStatus.NOT_FOUND, request)

        then:
        (response.body as ProblemDetail).properties.get("code") == "NOT_FOUND"
    }

    def "createResponseEntity leaves an already-coded ProblemDetail's code untouched"() {
        given:
        def problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        problem.setProperty("code", "ORDER_NOT_FOUND")

        when:
        handler.createResponseEntity(problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request)

        then:
        problem.properties.get("code") == "ORDER_NOT_FOUND"
    }

    def "createResponseEntity derives a code when the ProblemDetail has other properties but none named code"() {
        given:
        def problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        problem.setProperty("instanceId", "abc")

        when:
        handler.createResponseEntity(problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request)

        then:
        problem.properties.get("code") == "NOT_FOUND"
        problem.properties.get("instanceId") == "abc"
    }

    def "httpStatusFor maps every error category to its canonical HTTP status"() {
        expect:
        ApiErrorHandler.httpStatusFor(category) == status

        where:
        category                          || status
        ErrorCategory.INVALID_ARGUMENT    || HttpStatus.BAD_REQUEST
        ErrorCategory.FAILED_PRECONDITION || HttpStatus.BAD_REQUEST
        ErrorCategory.UNAUTHENTICATED     || HttpStatus.UNAUTHORIZED
        ErrorCategory.PERMISSION_DENIED   || HttpStatus.FORBIDDEN
        ErrorCategory.NOT_FOUND           || HttpStatus.NOT_FOUND
        ErrorCategory.CONFLICT            || HttpStatus.CONFLICT
        ErrorCategory.RESOURCE_EXHAUSTED  || HttpStatus.TOO_MANY_REQUESTS
        ErrorCategory.UNAVAILABLE         || HttpStatus.SERVICE_UNAVAILABLE
        ErrorCategory.INTERNAL            || HttpStatus.INTERNAL_SERVER_ERROR
    }

    def "deriveCodeFromStatus uses the status reason name, falling back to the numeric value for non-standard codes"() {
        expect:
        ApiErrorHandler.deriveCodeFromStatus(statusCode) == code

        where:
        statusCode                  || code
        HttpStatus.NOT_FOUND        || "NOT_FOUND"
        HttpStatus.BAD_REQUEST      || "BAD_REQUEST"
        HttpStatusCode.valueOf(599) || "599"
    }
}
