package org.libprunus.core.plugin.aot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.dynamic.ClassFileLocator;

public final class AotClassFileLocatorFactory {

    // Annotations on registry classes (in the libprunus-core jar) resolve only via the runtime classpath,
    // and TypePool silently drops any the locator can't resolve. Shared singleton — closing it breaks later builds.
    static final ClassFileLocator BOOT_LOADER_LOCATOR = ClassFileLocator.ForClassLoader.ofBootLoader();

    private AotClassFileLocatorFactory() {
        throw new UnsupportedOperationException();
    }

    private static ClassFileLocator create(Iterable<File> classesDirs, String targetCompatibility) throws IOException {
        ClassFileVersion classFileVersion =
                ClassFileVersion.ofJavaVersion(AsmClassFileVersionResolver.parseJavaMajor(targetCompatibility));
        List<ClassFileLocator> locators = new ArrayList<>();
        for (File dir : classesDirs) {
            locators.add(ClassFileLocator.ForFolder.of(dir, classFileVersion));
        }
        return new ClassFileLocator.Compound(locators);
    }

    public static ClassFileLocator create(
            Iterable<File> classesDirs, Iterable<File> runtimeClasspath, String targetCompatibility)
            throws IOException {
        ClassFileLocator classesLocator = create(classesDirs, targetCompatibility);
        return compose(classesLocator, runtimeClasspath);
    }

    public static ClassFileLocator compose(ClassFileLocator classesLocator, Iterable<File> runtimeClasspath) {
        List<ClassFileLocator> locators = new ArrayList<>();
        try {
            locators.add(classesLocator);
            for (File entry : runtimeClasspath) {
                if (entry == null || !entry.exists()) {
                    continue;
                }
                appendLocator(entry, locators);
            }
            locators.add(BOOT_LOADER_LOCATOR);
            return new ClassFileLocator.Compound(locators);
        } catch (Throwable t) {
            closeAllSuppressing(locators, t);
            throw t;
        }
    }

    static void appendFileLocators(Iterable<File> files, List<ClassFileLocator> sink) {
        for (File entry : files) {
            if (entry == null || !entry.exists()) {
                continue;
            }
            appendLocator(entry, sink);
        }
    }

    private static void appendLocator(File entry, List<ClassFileLocator> sink) {
        if (entry.isDirectory()) {
            sink.add(new ClassFileLocator.ForFolder(entry));
        } else if (entry.isFile() && entry.getName().endsWith(".jar")) {
            try {
                sink.add(ClassFileLocator.ForJarFile.of(entry));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to open JAR file: " + entry.getAbsolutePath(), e);
            }
        }
    }

    private static void closeAllSuppressing(List<ClassFileLocator> locators, Throwable primary) {
        for (ClassFileLocator locator : locators) {
            if (locator == BOOT_LOADER_LOCATOR) {
                continue;
            }
            try {
                locator.close();
            } catch (IOException e) {
                primary.addSuppressed(e);
            }
        }
    }
}
