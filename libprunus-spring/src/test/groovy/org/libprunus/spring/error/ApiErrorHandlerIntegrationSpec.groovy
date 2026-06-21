package org.libprunus.spring.error

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import org.libprunus.core.error.ApiErrorException
import org.libprunus.core.error.ErrorCategory
import org.libprunus.core.error.ErrorCode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

class ApiErrorHandlerIntegrationSpec extends Specification {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new ApiErrorHandler())
            .build()

    def "ApiErrorException renders the category status, code, and safe detail without leaking its internal cause"() {
        when:
        def response = mockMvc.perform(get("/api-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.status').value(404))
                .andExpect(jsonPath('$.code').value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath('$.detail').value("Order 42 not found"))
                .andReturn().response

        then: "the internal cause is never exposed to the caller"
        !response.contentAsString.contains("internal cause secret")
    }

    def "an unexpected exception renders as a generic INTERNAL problem that never leaks the internal message"() {
        when:
        def response = mockMvc.perform(get("/unexpected")).andReturn().response

        then: "the response is the generic internal fallback"
        response.status == 500
        response.contentAsString.contains('"code":"INTERNAL"')

        and: "the internal diagnostic message is not exposed to the caller"
        !response.contentAsString.contains("internal secret")
    }

    @RestController
    static class TestController {
        @GetMapping("/api-error")
        String apiError() {
            throw new ApiErrorException(
                    TestError.ORDER_NOT_FOUND, "Order 42 not found", new IllegalStateException("internal cause secret"))
        }

        @GetMapping("/unexpected")
        String unexpected() { throw new IllegalStateException("internal secret diagnostics") }
    }

    enum TestError implements ErrorCode {
        ORDER_NOT_FOUND(ErrorCategory.NOT_FOUND)

        private final ErrorCategory category

        TestError(ErrorCategory category) { this.category = category }

        @Override
        String code() { name() }

        @Override
        ErrorCategory category() { this.category }
    }
}
