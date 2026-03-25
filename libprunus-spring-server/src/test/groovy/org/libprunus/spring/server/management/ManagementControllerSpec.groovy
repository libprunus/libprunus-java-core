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
}
