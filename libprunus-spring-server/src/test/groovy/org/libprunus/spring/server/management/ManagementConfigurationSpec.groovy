package org.libprunus.spring.server.management

import spock.lang.Specification

class ManagementConfigurationSpec extends Specification {

    def "management configuration creates management controller bean"() {
        given: "a management configuration"
        def configuration = new ManagementConfiguration()

        when: "the management controller bean is requested"
        def controller = configuration.managementController()

        then: "the created controller responds with health payload"
        controller instanceof ManagementController
        controller.health().statusCode.is2xxSuccessful()
        controller.health().body == "health"
    }
}
