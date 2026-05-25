package org.libprunus.core.plugin.aot

import spock.lang.Specification

class AsmClassFileVersionResolverSpec extends Specification {

    def "resolve maps target compatibility to ASM class file version"() {
        expect:
        AsmClassFileVersionResolver.resolve(version) == expected

        where:
        version | expected
        "1.8"   | 52
        "8"     | 52
        "9"     | 53
        "10"    | 54
        "11"    | 55
        "12"    | 56
        "13"    | 57
        "14"    | 58
        "15"    | 59
        "16"    | 60
        "17"    | 61
        "18"    | 62
        "19"    | 63
        "20"    | 64
        "21"    | 65
        "22"    | 66
        "23"    | 67
        "24"    | 68
        "25"    | 69
    }

    def "parseJavaMajor extracts the Java major version consumed by ByteBuddy ClassFileVersion"() {
        expect:
        AsmClassFileVersionResolver.parseJavaMajor(version) == expected

        where:
        version | expected
        "1.8"   | 8
        "8"     | 8
        "11"    | 11
        "17"    | 17
        "21"    | 21
        "25"    | 25
    }
}
