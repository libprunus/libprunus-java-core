package org.libprunus.demo.server

import spock.lang.Specification

class DemoServerApplicationSpec extends Specification {

    def "main starts demo server application without throwing exceptions"() {
        given: "startup arguments for non-web mode"
        def args = ["--spring.main.web-application-type=none"] as String[]

        when: "the demo server main entrypoint is invoked directly"
        DemoServerApplication.main(args)

        then: "application startup completes for the entrypoint invocation"
        noExceptionThrown()
    }
}
