package org.libprunus.core.log.runtime

import spock.lang.Specification

class LogRuntimeConfigSpec extends Specification {

    def "Should round-trip the enabled gate through the generated accessor for both boolean polarities."() {
        expect:
        new LogRuntimeConfig(enabled).enabled() == enabled

        where:
        enabled << [true, false]
    }

    def "Should treat two LogRuntimeConfig instances as equal iff their enabled gates match."() {
        expect: "value equality polarity matches the project-layered contract that CoreRuntimeConfig.equals transitively depends on"
        (new LogRuntimeConfig(left) == new LogRuntimeConfig(right)) == expectedEqual

        and: "hashCode is consistent with equals — equal instances must hash identically"
        !expectedEqual || new LogRuntimeConfig(left).hashCode() == new LogRuntimeConfig(right).hashCode()

        where: "boolean × boolean Cartesian closure pins both same-gate equality and different-gate inequality"
        left  | right || expectedEqual
        true  | true  || true
        false | false || true
        true  | false || false
        false | true  || false
    }
}
