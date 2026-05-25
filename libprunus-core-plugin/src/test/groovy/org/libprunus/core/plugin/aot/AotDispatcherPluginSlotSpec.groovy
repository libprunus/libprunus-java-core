package org.libprunus.core.plugin.aot

import spock.lang.Specification

class AotDispatcherPluginSlotSpec extends Specification {

    def "slot bitMask is stable"() {
        expect:
        AotDispatcherPluginSlot.LOG.bitMask() == 1
    }

    def "values yields only the LOG constant"() {
        expect:
        AotDispatcherPluginSlot.values() == [AotDispatcherPluginSlot.LOG] as AotDispatcherPluginSlot[]
    }

}
