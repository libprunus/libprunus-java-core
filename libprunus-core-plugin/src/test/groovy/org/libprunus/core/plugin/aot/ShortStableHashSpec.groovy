package org.libprunus.core.plugin.aot

import spock.lang.Specification

class ShortStableHashSpec extends Specification {

    def "of returns lowercase hex of fixed prefix length regardless of input character set"() {
        expect:
        def hash = ShortStableHash.of(input)
        hash.length() == ShortStableHash.HASH_BYTES * 2
        hash ==~ /^[0-9a-f]{32}$/

        where:
        input          | _
        "sample.input" | _
        ""             | _
        "主键"         | _
        "mixed-主键"   | _
    }

    def "of is deterministic for the same input"() {
        when:
        def first = ShortStableHash.of("repeatable")
        def second = ShortStableHash.of("repeatable")

        then:
        first == second
    }

    def "of produces distinct hashes for distinct inputs across character sets"() {
        expect:
        ShortStableHash.of(left) != ShortStableHash.of(right)

        where:
        left    | right
        "alpha" | "beta"
        "alpha" | "主键"
        "主键"  | "🌸"
    }

    def "newSha256 returns a digest configured for the SHA-256 algorithm"() {
        when:
        def digest = ShortStableHash.newSha256()

        then:
        digest.algorithm == "SHA-256"
    }

    def "newSha256 returns a fresh instance on each invocation"() {
        when:
        def first = ShortStableHash.newSha256()
        def second = ShortStableHash.newSha256()

        then:
        !first.is(second)
    }
}
