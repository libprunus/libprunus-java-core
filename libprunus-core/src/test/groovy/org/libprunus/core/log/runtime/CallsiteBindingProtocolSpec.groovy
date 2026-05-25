package org.libprunus.core.log.runtime

import spock.lang.Specification

class CallsiteBindingProtocolSpec extends Specification {

    def "each AOT callsite coordinate matches its published literal and carries no path separator artifacts"() {
        expect:
        pinned == published
        !pinned.startsWith("/")
        !pinned.endsWith("/")
        !pinned.contains("//")
        !filenameOnly || !pinned.contains("/")

        where:
        pinned                                    || published                                          | filenameOnly
        CallsiteBindingProtocol.RESOURCE_DIR      || "META-INF/prunus/aot"                              | false
        CallsiteBindingProtocol.RESOURCE_FILENAME || "runtime-binding-callsite"                         | true
        CallsiteBindingProtocol.RESOURCE_PATH     || "META-INF/prunus/aot/runtime-binding-callsite"     | false
    }

    def "RESOURCE_PATH is composed exactly of RESOURCE_DIR, separator, and RESOURCE_FILENAME"() {
        expect:
        CallsiteBindingProtocol.RESOURCE_PATH ==
            CallsiteBindingProtocol.RESOURCE_DIR + "/" + CallsiteBindingProtocol.RESOURCE_FILENAME
    }
}
