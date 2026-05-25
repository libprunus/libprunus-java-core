package org.libprunus.core.log.runtime

import org.libprunus.core.log.runtime.fixture.RecoverToStringFallbackInnerFailureProbe
import org.libprunus.core.log.runtime.fixture.StringBuilderWithContextFallbackThrowableSelectionProbe
import org.libprunus.core.log.runtime.fixture.StringBuilderWithContextToStringOomSelectionProbe
import spock.lang.IgnoreIf
import spock.lang.Specification

class StringBuilderWithContextFallbackThrowableSelectionIntegrationSpec extends Specification {

    private static final int MAX_VERIFIED_JAVA_MAJOR = 25

    def "separate JVM verifies toString fallback throwable selection semantics"() {
        given:
        def javaBin = new File(System.getProperty("java.home"), "bin/java").absolutePath
        def classpath = System.getProperty("java.class.path")
        def process = new ProcessBuilder(
                        javaBin, "-cp", classpath, StringBuilderWithContextFallbackThrowableSelectionProbe.name)
                .redirectErrorStream(true)
                .start()

        when:
        def stdout = process.inputStream.getText("UTF-8")
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        stdout.contains("FALLBACK_THROWABLE_SELECTION_OK")
        !stdout.contains("FALLBACK_THROWABLE_SELECTION_FAILED")
    }

    def "separate JVM under real heap exhaustion verifies toString-failure OOM is selected as primary over the original non-Error throwable"() {
        given:
        def javaBin = new File(System.getProperty("java.home"), "bin/java").absolutePath
        def classpath = System.getProperty("java.class.path")
        def process = new ProcessBuilder(
                        javaBin,
                        "-Xmx32m",
                        "-cp",
                        classpath,
                        StringBuilderWithContextToStringOomSelectionProbe.name)
                .redirectErrorStream(true)
                .start()

        when:
        def stdout = process.inputStream.getText("UTF-8")
        def exitCode = process.waitFor()

        then:
        exitCode == 0
        stdout.contains("TOSTRING_OOM_SELECTION_OK")
        !stdout.contains("TOSTRING_OOM_SELECTION_FAILED")
    }

    @IgnoreIf({ runtimeJavaMajor() > MAX_VERIFIED_JAVA_MAJOR })
    def "recoverToStringFallback rethrows toString-Error suppressing the original throwable in a low-heap subprocess"() {
        when:
        def (exitCode, stdout) = runProbe(
                RecoverToStringFallbackInnerFailureProbe,
                ["--add-opens", "java.base/java.lang=ALL-UNNAMED"],
                "inner-non-soe-error")

        then: "the corrupted toString raises OOM and that exact Error type propagates back out of recoverToStringFallback"
        exitCode == 0
        stdout.contains("INNER_NON_SOE_ERROR_OK")
        stdout.contains("ERROR_TYPE=java.lang.OutOfMemoryError")
        !stdout.contains("INNER_NON_SOE_ERROR_FAILED")
    }

    @IgnoreIf({ runtimeJavaMajor() > MAX_VERIFIED_JAVA_MAJOR })
    def "recoverToStringFallback returns empty fallback and still releases when builder toString throws non-Error throwable in subprocess"() {
        when:
        def (exitCode, stdout) = runProbe(
                RecoverToStringFallbackInnerFailureProbe,
                ["--add-opens", "java.base/java.lang=ALL-UNNAMED"],
                "inner-non-error-throwable")

        then: "the IllegalArgumentException from the corrupted toString enters the non-Error catch branch (logged by LoggingFailureReporter to the merged stream) and line 491 release runs (observed via the AIOOBE leaking from setLength(0) on the still-corrupted count)"
        exitCode == 0
        stdout.contains("INNER_NON_ERROR_THROWABLE_OK")
        stdout.contains("INNER_CATCH_BRANCH_RAN=true")
        stdout.contains("RELEASE_AIOOBE_OBSERVED=true")
        stdout.contains("libprunus logging failure at probe")
        stdout.contains("java.lang.IllegalArgumentException")
        !stdout.contains("INNER_NON_ERROR_THROWABLE_FAILED")
    }

    private static int runtimeJavaMajor() {
        String spec = System.getProperty("java.specification.version")
        return spec.startsWith("1.") ? Integer.parseInt(spec.substring(2)) : Integer.parseInt(spec)
    }

    private static List runProbe(Class<?> probeClass, List<String> jvmArgs, String... probeArgs) {
        def javaBin = new File(System.getProperty("java.home"), "bin/java").absolutePath
        def classpath = System.getProperty("java.class.path")
        def command = [javaBin] + jvmArgs + ["-cp", classpath, probeClass.name] + (probeArgs as List)
        def process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        def stdout = process.inputStream.getText("UTF-8")
        int exitCode = process.waitFor()
        return [exitCode, stdout]
    }
}
