package org.libprunus.core.plugin.aot.log;

import java.util.List;
import org.libprunus.core.log.runtime.LogLevel;

final class MethodLoggingRule {

    private final String routeId;
    private final List<String> includePackages;
    private final List<String> excludePackages;
    private final List<String> includeClassSuffixes;
    private final LogLevel entryLevel;
    private final LogLevel exitLevel;
    private final List<FieldExtractorRef> fieldExtractors;

    MethodLoggingRule(
            String routeId,
            List<String> includePackages,
            List<String> excludePackages,
            List<String> includeClassSuffixes,
            LogLevel entryLevel,
            LogLevel exitLevel,
            List<FieldExtractorRef> fieldExtractors) {
        this.routeId = routeId;
        this.includePackages = List.copyOf(includePackages);
        this.excludePackages = List.copyOf(excludePackages);
        this.includeClassSuffixes = List.copyOf(includeClassSuffixes);
        this.entryLevel = entryLevel;
        this.exitLevel = exitLevel;
        this.fieldExtractors = List.copyOf(fieldExtractors);
    }

    String routeId() {
        return routeId;
    }

    LogLevel entryLevel() {
        return entryLevel;
    }

    LogLevel exitLevel() {
        return exitLevel;
    }

    List<FieldExtractorRef> fieldExtractors() {
        return fieldExtractors;
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
