package org.libprunus.spring.server.management

import spock.lang.Specification

class ManagementControllerSpec extends Specification {

    def "health returns ok response with health body"() {
        given: "a management controller instance"
        def controller = new ManagementController()

        when: "the health endpoint is invoked"
        def result = controller.health()

        then: "an OK response with health payload is returned"
        result.statusCode.is2xxSuccessful()
        result.body == "health"
    }

    def "health remains deterministic across repeated invocations"() {
        given: "a management controller instance"
        def controller = new ManagementController()

        when: "the health endpoint is invoked multiple times"
        def responses = (1..invocationCount).collect { controller.health() }

        then: "all responses are successful and have the same payload"
        responses.size() == invocationCount
        responses.every { it.statusCode.is2xxSuccessful() }
        responses.every { it.body == "health" }

        where:
        invocationCount << [1, 2, 5]
    }
}
