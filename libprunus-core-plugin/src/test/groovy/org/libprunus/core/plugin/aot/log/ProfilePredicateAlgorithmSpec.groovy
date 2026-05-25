package org.libprunus.core.plugin.aot.log

import spock.lang.Specification

class ProfilePredicateAlgorithmSpec extends Specification {

    def "anyPackagePrefixMatches returns false when the prefix list is empty regardless of package name"() {
        expect:
        !ProfilePredicate.anyPackagePrefixMatches([], "com.foo")
        !ProfilePredicate.anyPackagePrefixMatches([], "")
    }

    def "anyPackagePrefixMatches returns true when the package name equals a prefix entry exactly"() {
        expect:
        ProfilePredicate.anyPackagePrefixMatches(["com.foo"], "com.foo")
    }

    def "anyPackagePrefixMatches returns true when the package name is a sub-package of an entry honoring the dot boundary"() {
        expect:
        ProfilePredicate.anyPackagePrefixMatches(["com"], "com.foo")
        ProfilePredicate.anyPackagePrefixMatches(["com.foo"], "com.foo.bar")
    }

    def "anyPackagePrefixMatches returns false when the prefix is a literal prefix string but breaks the dot boundary"() {
        expect:
        !ProfilePredicate.anyPackagePrefixMatches(["com.fo"], "com.foo")
    }

    def "anyPackagePrefixMatches normalizes a trailing dot in the prefix before matching"() {
        expect:
        ProfilePredicate.anyPackagePrefixMatches(["com.foo."], "com.foo")
        ProfilePredicate.anyPackagePrefixMatches(["com.foo."], "com.foo.bar")
    }

    def "anyPackagePrefixMatches treats an empty string prefix as a non-match so the empty entry never matches anything"() {
        expect:
        !ProfilePredicate.anyPackagePrefixMatches([""], "com.foo")
        !ProfilePredicate.anyPackagePrefixMatches([""], "")
    }

    def "anyPackagePrefixMatches treats a sole dot prefix as a non-match because normalization yields an empty entry"() {
        expect:
        !ProfilePredicate.anyPackagePrefixMatches(["."], "com.foo")
    }

    def "anyPackagePrefixMatches strips only the single trailing dot so a double-trailing-dot prefix does not collapse to the bare package"() {
        expect:
        !ProfilePredicate.anyPackagePrefixMatches(["com.foo.."], "com.foo")
    }

    def "anyPackagePrefixMatches returns true when any entry in a mixed list matches even if other entries miss"() {
        expect:
        ProfilePredicate.anyPackagePrefixMatches(["com.bar", "com.foo"], "com.foo")
    }

    def "anyPackagePrefixMatches returns true when a middle entry of a mixed list matches without consulting later entries"() {
        expect:
        ProfilePredicate.anyPackagePrefixMatches(["com.bar", "com.foo", "com.baz"], "com.foo")
    }

    def "anyPackagePrefixMatches still finds a valid entry when an earlier empty-string entry yields no match"() {
        expect:
        ProfilePredicate.anyPackagePrefixMatches(["", "com.foo"], "com.foo")
    }

    def "anyPackagePrefixMatches returns false when no entry matches the package"() {
        expect:
        !ProfilePredicate.anyPackagePrefixMatches(["com.bar", "org.example"], "com.foo")
    }

    def "classNameSuffixMatches returns false when the suffix list is empty regardless of class name"() {
        expect:
        !ProfilePredicate.classNameSuffixMatches([], "Outer\$Inner")
        !ProfilePredicate.classNameSuffixMatches([], "")
    }

    def "classNameSuffixMatches returns true when the class name ends with the listed suffix"() {
        expect:
        ProfilePredicate.classNameSuffixMatches(["Service"], "OrderService")
    }

    def "classNameSuffixMatches returns true when the class name equals a suffix entry exactly"() {
        expect:
        ProfilePredicate.classNameSuffixMatches(["Outer\$Inner"], "Outer\$Inner")
    }

    def "classNameSuffixMatches honors the dollar sign as a literal character in the suffix when the inner class name is matched"() {
        expect:
        ProfilePredicate.classNameSuffixMatches(["Inner"], "Outer\$Inner")
        ProfilePredicate.classNameSuffixMatches(["\$Inner"], "Outer\$Inner")
    }

    def "classNameSuffixMatches returns false when the candidate ends with a non-listed segment even though listed suffix appears earlier"() {
        expect:
        !ProfilePredicate.classNameSuffixMatches(["Outer"], "Outer\$Inner")
    }

    def "classNameSuffixMatches returns true when any entry in a mixed list matches the class name end"() {
        expect:
        ProfilePredicate.classNameSuffixMatches(["Repository", "Service"], "OrderService")
    }

    def "classNameSuffixMatches returns true when a middle entry of a mixed list matches without consulting later entries"() {
        expect:
        ProfilePredicate.classNameSuffixMatches(["Repository", "Service", "Controller"], "OrderService")
    }

    def "classNameSuffixMatches returns false when no entry matches the class name end"() {
        expect:
        !ProfilePredicate.classNameSuffixMatches(["Repository", "Controller"], "OrderService")
    }

    def "classNameSuffixMatches treats an empty string suffix as a non-match so the empty entry never matches anything"() {
        expect:
        !ProfilePredicate.classNameSuffixMatches([""], "OrderService")
        !ProfilePredicate.classNameSuffixMatches([""], "")
    }

    def "classNameSuffixMatches still finds a valid entry when an earlier empty-string entry yields no match"() {
        expect:
        ProfilePredicate.classNameSuffixMatches(["", "Service"], "OrderService")
    }
}
