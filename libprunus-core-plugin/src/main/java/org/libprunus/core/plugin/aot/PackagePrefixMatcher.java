package org.libprunus.core.plugin.aot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PackagePrefixMatcher {

    private final boolean matchAll;
    private final String[] sortedPackages;

    public PackagePrefixMatcher(List<String> packages, boolean matchAllWhenEmpty) {
        this.matchAll = matchAllWhenEmpty && (packages == null || packages.isEmpty());
        if (matchAll || packages == null) {
            this.sortedPackages = new String[0];
            return;
        }
        String[] candidates = packages.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .toArray(String[]::new);
        this.sortedPackages = compactPrefixes(candidates);
    }

    public boolean matches(String className) {
        if (matchAll) {
            return true;
        }
        if (className == null || className.isEmpty()) {
            return false;
        }
        int low = 0;
        int high = sortedPackages.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = sortedPackages[mid];
            int comparison = candidate.compareTo(className);
            if (comparison == 0) {
                return true;
            }
            if (comparison < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (high < 0) {
            return false;
        }
        String candidate = sortedPackages[high];
        return className.startsWith(candidate)
                && className.length() > candidate.length()
                && className.charAt(candidate.length()) == '.';
    }

    private static String[] compactPrefixes(String[] sortedCandidates) {
        if (sortedCandidates.length <= 1) {
            return sortedCandidates;
        }
        List<String> compacted = new ArrayList<>(sortedCandidates.length);
        for (String candidate : sortedCandidates) {
            if (compacted.isEmpty()) {
                compacted.add(candidate);
                continue;
            }
            String previous = compacted.get(compacted.size() - 1);
            if (isCoveredByPrefix(candidate, previous)) {
                continue;
            }
            compacted.add(candidate);
        }
        return compacted.toArray(String[]::new);
    }

    private static boolean isCoveredByPrefix(String candidate, String prefix) {
        return candidate.startsWith(prefix)
                && candidate.length() > prefix.length()
                && candidate.charAt(prefix.length()) == '.';
    }
}
