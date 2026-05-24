package org.libprunus.core.plugin.aot;

import org.gradle.api.JavaVersion;

public final class AsmClassFileVersionResolver {

    // WHY: class-file major version = 44 + Java major (JVMS §4.1; Java 1 → 45, Java 25 → 69).
    private static final int CLASS_FILE_VERSION_OFFSET_FROM_JAVA_MAJOR = 44;

    private AsmClassFileVersionResolver() {
        throw new UnsupportedOperationException();
    }

    public static int resolve(String targetCompatibility) {
        return CLASS_FILE_VERSION_OFFSET_FROM_JAVA_MAJOR + parseJavaMajor(targetCompatibility);
    }

    static int parseJavaMajor(String targetCompatibility) {
        return Integer.parseInt(JavaVersion.toVersion(targetCompatibility).getMajorVersion());
    }
}
