package org.libprunus.core.plugin.aot

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import spock.lang.Specification

class BindingIdSanitizerSpec extends Specification {

    private static String expectedHashHex(String value) {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest, 0, 16)
    }

    def "sanitizeForPackageSegment converts invalid characters and appends stable hash suffix"() {
        given:
        String bindingId = "binding-id@2024!test"

        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(bindingId)

        then:
        result ==~ /binding_id_2024_test_[0-9a-f]{32}/
    }

    def "sanitizeForPackageSegment returns input unchanged when strip yields an already valid Java identifier"() {
        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(input)

        then:
        result == expected
        !(result ==~ /.*_[0-9a-f]{32}$/)

        where:
        input                    || expected
        'valid_BindingId_123_$'  || 'valid_BindingId_123_$'
        'binding$inner$class'    || 'binding$inner$class'
        '_already_ok'            || '_already_ok'
        'A'                      || 'A'
        '  binding_id  '         || 'binding_id'
    }

    def "sanitizeForPackageSegment appends trailing underscore plus hash for every declared reserved segment"() {
        given:
        String expectedSuffix = expectedHashHex(word)

        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(word)

        then:
        result == "${word}__${expectedSuffix}"

        where:
        word << [
            "_", "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "module",
            "native", "new", "null", "package", "private", "protected", "public",
            "record", "requires", "return", "sealed", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "to",
            "transient", "true", "transitive", "try", "uses", "var", "void", "volatile",
            "while", "with", "yield"
        ]
    }

    def "sanitizeForPackageSegment blank or whitespace-only input fails with project-declared IllegalArgumentException message"() {
        when:
        BindingIdSanitizer.sanitizeForPackageSegment(value)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "bindingId must not be blank"

        where:
        value << ["", "   ", "\t", "\n", "　"]
    }

    def "sanitizeForPackageSegment null input fails fast through the project-declared requireNonNull guard"() {
        when:
        BindingIdSanitizer.sanitizeForPackageSegment(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "bindingId"
    }

    def "sanitizeForPackageSegment prepends underscore when leading character is a digit"() {
        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(value)

        then:
        result.startsWith("_")
        result ==~ /_[0-9][0-9a-zA-Z_$]*_[0-9a-f]{32}/

        where:
        value << ["123binding", "9abc", "0z"]
    }

    def "sanitizeForPackageSegment keeps first character when it is already a Java identifier start even if later characters are replaced"() {
        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(input)

        then:
        !result.startsWith("__")
        result ==~ (expectedBaseRegex + '_[0-9a-f]{32}')

        where:
        input              || expectedBaseRegex
        "binding-id@2024"  || 'binding_id_2024'
        '$weird!name'      || '\\$weird_name'
        "_already_ok!x"    || '_already_ok_x'
    }

    def "sanitizeForPackageSegment scrubs every non-identifier character to underscore while preserving dollar and underscore"() {
        given:
        String expectedSuffix = expectedHashHex(input.strip())

        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(input)

        then:
        result == expectedBase + "_" + expectedSuffix

        where:
        input                                       || expectedBase
        'binding!@#$%^&*()-+=[]{}|;:\'\'.<>?/~`'    || 'binding___$_________________________'
        '@valid'                                    || '_valid'
        '.dotted.name'                              || '_dotted_name'
        'has space'                                 || 'has_space'
    }

    def "sanitizeForPackageSegment derives hash suffix from stripped input so identical normalized inputs map identically and base-collisions diverge"() {
        when:
        def left = BindingIdSanitizer.sanitizeForPackageSegment(leftInput)
        def right = BindingIdSanitizer.sanitizeForPackageSegment(rightInput)

        then:
        (left == right) == sameOutput
        left.startsWith("${sharedBase}_") || left == sharedBase
        right.startsWith("${sharedBase}_") || right == sharedBase

        where:
        leftInput           | rightInput         | sharedBase        || sameOutput
        'binding-id@2024'   | 'binding-id@2024'  | 'binding_id_2024' || true
        'my-variant'        | 'my.variant'       | 'my_variant'      || false
        '  my-variant  '    | 'my-variant'       | 'my_variant'      || true
    }

    def "sanitizeForPackageSegment hash suffix equals first sixteen bytes of SHA-256 of the trimmed input"() {
        given:
        String bindingId = "binding-id@2024!test"
        String expectedSuffix = expectedHashHex("binding-id@2024!test")

        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(bindingId)

        then:
        result.endsWith("_" + expectedSuffix)
        result.length() - "binding_id_2024_test_".length() == 32
    }

    def "sanitizeForPackageSegment produces valid Java identifier"() {
        given:
        String bindingId = value

        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(bindingId)

        then:
        Character.isJavaIdentifierStart(result.charAt(0))
        result.toCharArray().every { Character.isJavaIdentifierPart(it) }

        where:
        value << [
            "valid_id",
            "123invalid",
            "binding!id",
            "path/to/binding",
            "binding-@-id",
            "_underscoreStart",
            '$dollarSign',
            "UPPERCASE_ID"
        ]
    }

    def "sanitizeForPackageSegment scrubs non-ASCII characters to underscore"() {
        given:
        String bindingId = "binding_café_2024"
        String expectedSuffix = expectedHashHex("binding_café_2024")

        when:
        def result = BindingIdSanitizer.sanitizeForPackageSegment(bindingId)

        then:
        !result.contains("é")
        result == "binding_caf__2024_" + expectedSuffix
    }
}
