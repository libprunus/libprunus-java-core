package org.libprunus.core.plugin.aot.log;

import java.util.List;

final class ProfilePredicate {

    private ProfilePredicate() {
        throw new UnsupportedOperationException();
    }

    static boolean anyPackagePrefixMatches(List<String> prefixes, String packageName) {
        if (prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (matchesPackage(prefix, packageName)) {
                return true;
            }
        }
        return false;
    }

    static boolean classNameSuffixMatches(List<String> suffixes, String className) {
        if (suffixes.isEmpty()) {
            return false;
        }
        for (String suffix : suffixes) {
            if (className.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPackage(String prefix, String packageName) {
        if (prefix.isEmpty()) {
            return false;
        }
        String normalized = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (normalized.isEmpty()) {
            return false;
        }
        if (packageName.equals(normalized)) {
            return true;
        }
        return packageName.startsWith(normalized + ".");
    }
}
