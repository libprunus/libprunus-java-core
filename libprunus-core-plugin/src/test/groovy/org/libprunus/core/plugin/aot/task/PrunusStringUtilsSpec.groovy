package org.libprunus.core.plugin.aot.task

import spock.lang.Specification

class PrunusStringUtilsSpec extends Specification {

    def "normalize returns null when input is null or blank across whitespace variants"() {
        expect:
        PrunusStringUtils.normalize(input) == null

        where:
        input << [null, "", " ", "  ", "\t", "\n", "\r\n", " \t \n ", "　", "　　"]
    }

    def "normalize strips leading and trailing whitespace and returns inner payload"() {
        expect:
        PrunusStringUtils.normalize(input) == expected

        where:
        input                       || expected
        "  org.example.Binding  "   || "org.example.Binding"
        "result　"                  || "result"
        "　result"                  || "result"
        "　org.example.Binding　"   || "org.example.Binding"
    }

    def "normalize preserves internal whitespace within non-blank payload"() {
        expect:
        PrunusStringUtils.normalize("  org.example  Binding  ") == "org.example  Binding"
    }
}
