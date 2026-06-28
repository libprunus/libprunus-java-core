package org.libprunus.core.plugin.testutil

/**
 * The build-local Maven repository holding the fixed-version libprunus-core artifact
 * the plugin's TestKit specs resolve. Repo layout and version are build constants, so
 * specs derive them here directly instead of depending on a system property the test
 * runner (e.g. PIT's forked coverage minion) may not set.
 */
final class IntegrationTestRepo {

    static final String CORE_VERSION = '0.0.1-integration-test'

    private static File dir() {
        File current = new File(System.getProperty('user.dir')).canonicalFile
        while (current != null && !new File(current, 'settings.gradle.kts').exists()) {
            current = current.parentFile
        }
        assert current != null : 'repo root (settings.gradle.kts) not found from user.dir'
        new File(current, 'build/plugin-integration-test-repo')
    }

    static String escapedPath() {
        dir().absolutePath.replace('\\', '\\\\')
    }

    private IntegrationTestRepo() {
    }
}
