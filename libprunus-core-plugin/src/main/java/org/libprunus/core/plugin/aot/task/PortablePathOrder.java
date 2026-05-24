package org.libprunus.core.plugin.aot.task;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class PortablePathOrder {

    private static final Comparator<Map.Entry<File, SortKey>> ENTRY_COMPARATOR =
            Comparator.comparing(Map.Entry::getValue, SortKey.ORDER);

    private PortablePathOrder() {
        throw new UnsupportedOperationException();
    }

    public static List<File> sortByProjectRelativePath(List<File> files, Path projectDir) {
        Path normalizedProjectDir = projectDir.normalize().toAbsolutePath();
        return sortBy(files, file -> projectRelativeSortKey(normalizedProjectDir, file));
    }

    public static List<File> sortByPortableTailPath(List<File> files) {
        return sortBy(files, PortablePathOrder::portableTailSortKey);
    }

    private static List<File> sortBy(List<File> files, Function<File, SortKey> extractor) {
        return files.stream()
                .map(file -> Map.entry(file, extractor.apply(file)))
                .sorted(ENTRY_COMPARATOR)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static SortKey projectRelativeSortKey(Path projectDir, File file) {
        Path normalizedFilePath = normalizedAbsolutePath(file);
        if (normalizedFilePath.startsWith(projectDir)) {
            String relative = toPortablePath(projectDir.relativize(normalizedFilePath));
            return new SortKey("0|" + relative, "");
        }
        String portableTail = portableTail(normalizedFilePath, 8);
        return new SortKey("1|" + portableTail, toPortablePath(normalizedFilePath));
    }

    private static SortKey portableTailSortKey(File file) {
        Path normalizedFilePath = normalizedAbsolutePath(file);
        String primary = file.getName() + "|" + portableTail(normalizedFilePath, 8);
        return new SortKey(primary, toPortablePath(normalizedFilePath));
    }

    private static Path normalizedAbsolutePath(File file) {
        return file.toPath().normalize().toAbsolutePath();
    }

    private static String portableTail(Path path, int segmentCount) {
        int nameCount = path.getNameCount();
        if (nameCount == 0) {
            return "";
        }
        int from = Math.max(0, nameCount - segmentCount);
        Path tail = path.subpath(from, nameCount);
        return toPortablePath(tail);
    }

    private static String toPortablePath(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }

    private record SortKey(String primary, String secondary) {

        private static final Comparator<SortKey> ORDER =
                Comparator.comparing(SortKey::primary).thenComparing(SortKey::secondary);
    }
}
