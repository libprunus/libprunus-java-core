package org.libprunus.core.plugin.aot

import spock.lang.Specification

class BindingIdGeneratorAlgorithmSpec extends Specification {

    def "generate produces distinct hashes when group and artifact values are swapped"() {
        given:
        def generator = new BindingIdGenerator()

        expect:
        generator.generate(groupA, artifactA, "v", ":m", "main") !=
                generator.generate(groupB, artifactB, "v", ":m", "main")

        where:
        groupA | artifactA || groupB | artifactB
        "A"    | "B"       || "B"    | "A"
        "g"    | "A"       || "g"    | "B"
    }

    def "generate keeps pipe and colon characters distinct from the internal 0x00 field separator"() {
        given:
        def generator = new BindingIdGenerator()

        expect:
        generator.generate(groupA, artifactA, "1.0", ":service", "main") !=
                generator.generate(groupB, artifactB, "1.0", ":service", "main")

        where:
        groupA      | artifactA || groupB      | artifactB
        "org|demo"  | "app"     || "org.demo"  | "app|version"
        "org:demo"  | "app"     || "org.demo"  | "app:lib"
    }

    def "generate maps unspecified equivalence class of coordinates to the same hash"() {
        given:
        def generator = new BindingIdGenerator()
        def baseline = generator.generate("unspecified", "unspecified", "unspecified", ":service", "main")

        expect:
        generator.generate(group, artifact, version, ":service", "main") == baseline

        where:
        group     | artifact  | version
        null      | null      | null
        ""        | ""        | ""
        "  "      | "  "      | "  "
        "　"  | "　"  | "　"
    }

    def "generate strips leading and trailing whitespace from each coordinate before hashing"() {
        given:
        def generator = new BindingIdGenerator()
        def baseline = generator.generate("org", "app", "1.0", ":service", "main")

        expect:
        generator.generate(group, artifact, version, modulePath, variant) == baseline

        where:
        group        | artifact   | version    | modulePath       | variant
        "  org  "    | "app"      | "1.0"      | ":service"       | "main"
        "org"        | "  app  "  | "1.0"      | ":service"       | "main"
        "org"        | "app"      | "  1.0  "  | ":service"       | "main"
        "org"        | "app"      | "1.0"      | "　:service　" | "main"
        "org"        | "app"      | "1.0"      | ":service"       | " main "
    }

    def "generate output format is always b followed by 32 hex characters"() {
        given:
        def generator = new BindingIdGenerator()

        expect:
        generator.generate(group, artifact, version, modulePath, variant) ==~ /b[0-9a-f]{32}/

        where:
        group      | artifact | version | modulePath           | variant
        "g1"       | "a1"     | "v1"    | ":m1"                | "variant1"
        "g2"       | "a2"     | "v2"    | ":m2"                | "variant2"
        null       | null     | null    | ":m3"                | "variant3"
        "  org  "  | "  app  "| "  1.0  " | ":complex-path_123" | "complex-variant_456"
    }
}
