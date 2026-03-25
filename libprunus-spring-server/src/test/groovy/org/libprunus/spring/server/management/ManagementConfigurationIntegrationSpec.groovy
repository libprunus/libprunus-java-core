package org.libprunus.spring.server.management

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification

class ManagementConfigurationIntegrationSpec extends Specification {

    def "context registers management controller bean and exposes expected behavior"() {
        given: "an application context bootstrapped with management configuration"
        def context = new AnnotationConfigApplicationContext(ManagementConfiguration)

        when: "the management controller bean is retrieved from the context"
        def controller = context.getBean(ManagementController)

        then: "the controller returns an OK health response"
        controller.health().statusCode.is2xxSuccessful()
        controller.health().body == "health"

        cleanup:
        context.close()
    }
}
