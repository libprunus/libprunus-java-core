package org.libprunus.core.plugin.aot.log;

import java.util.List;

final class ToStringRule {

    private final String routeId;
    private final List<String> includePackages;
    private final List<String> excludePackages;
    private final List<String> includeClassSuffixes;

    ToStringRule(
            String routeId,
            List<String> includePackages,
            List<String> excludePackages,
            List<String> includeClassSuffixes) {
        this.routeId = routeId;
        this.includePackages = List.copyOf(includePackages);
        this.excludePackages = List.copyOf(excludePackages);
        this.includeClassSuffixes = List.copyOf(includeClassSuffixes);
    }

    String routeId() {
        return routeId;
    }

    boolean matches(String packageName, String className) {
        if (!ProfilePredicate.anyPackagePrefixMatches(includePackages, packageName)) {
            return false;
        }
        if (ProfilePredicate.anyPackagePrefixMatches(excludePackages, packageName)) {
            return false;
        }
        return ProfilePredicate.classNameSuffixMatches(includeClassSuffixes, className);
    }
}
