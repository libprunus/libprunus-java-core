package org.libprunus.core.log.runtime

import spock.lang.Specification

class LogRuntimeBindingIntegrationSpec extends Specification {

    def "separate JVM probe verifies binding fields are volatile and getters preserve binding semantics"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeBindingVisibilityProbe)

        then:
        exitCode == 0
        stdout.contains("BINDING_VISIBILITY_OK")
        stdout.contains("configVolatile=true")
        stdout.contains("maxLengthVolatile=true")
        stdout.contains("activeConfigRefVolatile=true")
        stdout.contains("bindingInitializedVolatile=true")
        stdout.contains("getterValuesMatch=true")
    }

    def "fresh runtime default binding is overridden by initializeBinding in separate JVM process"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeBindingProbe)

        then:
        exitCode == 0
        stdout.contains("BEFORE_MAX_LENGTH=512")
        stdout.contains("BEFORE_WHITELISTED_OBJECT=false")
        stdout.contains("AFTER_MAX_LENGTH=1024")
        stdout.contains("AFTER_WHITELISTED_OBJECT=true")
    }

    def "separate JVM process rejects repeated initializeBinding calls"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeBindingProbe, "repeat")

        then:
        exitCode != 0
        stdout.contains("IllegalStateException")
        stdout.contains("LogRuntime binding has already been initialized")
    }

    def "separate JVM process rejects binding maxMessageLength outside the allowed range"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeBindingProbe, probeMode)

        then:
        exitCode != 0
        stdout.contains("IllegalArgumentException")
        stdout.contains(expectedMessageFragment)

        where:
        probeMode            || expectedMessageFragment
        "invalid-length"     || "binding maxMessageLength must be >= 16"
        "invalid-length-max" || "binding maxMessageLength must be <= 1048576"
    }

    def "fresh JVM keeps first binding when a repeated initializeBinding call is rejected"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeBindingProbe, "repeat-state-check")

        then:
        exitCode == 0
        stdout.contains("CAUGHT_TYPE=IllegalStateException")
        stdout.contains("CAUGHT_MESSAGE=LogRuntime binding has already been initialized")
        stdout.contains("POST_MAX_LENGTH=1024")
        stdout.contains("POST_WHITELISTED_OBJECT=true")
        stdout.contains("POST_IS_DEFAULT=false")
    }

    def "fresh JVM keeps binding at DEFAULT when initializeBinding validation fails"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeBindingProbe, probeMode)

        then:
        exitCode == 0
        stdout.contains("CAUGHT_TYPE=IllegalArgumentException")
        stdout.contains("CAUGHT_MESSAGE=" + expectedMessage)
        stdout.contains("POST_MAX_LENGTH=512")
        stdout.contains("POST_WHITELISTED_OBJECT=false")
        stdout.contains("POST_IS_DEFAULT=true")

        where:
        probeMode                        | expectedMessage
        "invalid-length-state-check"     | "binding maxMessageLength must be >= 16: 15"
        "invalid-length-max-state-check" | "binding maxMessageLength must be <= 1048576: 1048577"
    }

    def "fresh JVM with concurrent initializeBinding callers admits exactly one winner and rejects the rest with IllegalStateException"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeConcurrentInitProbe)

        then: "the probe ran to completion in the fresh JVM"
        exitCode == 0

        and: "exactly one of the concurrent callers won the once-only initializeBinding race"
        stdout.contains("WINNERS=1")

        and: "all losing callers received the specific IllegalStateException the once-only guard publishes — none observed any other throwable type"
        stdout.contains("ISE_COUNT=15")
        stdout.contains("OTHER_THROWABLES=0")

        and: "the winning binding is the probe-supplied binding (max=4096) — proving that one call did mutate the global state"
        stdout.contains("BINDING_MAX_LENGTH=4096")
        stdout.contains("BINDING_IS_DEFAULT=false")
    }

    def "fresh JVM observes linkToDataPlane updates from a reader thread without NPE or value tearing"() {
        when:
        def (exitCode, stdout) = runProbe(LogRuntimeDataPlaneVisibilityProbe)

        then: "the probe ran to completion in the fresh JVM"
        exitCode == 0

        and: "the reader observed both boolean polarities — proving writer's set() became visible cross-thread on subsequent isEnabled() reads"
        stdout.contains("OBSERVED_ENABLED_TRUE=true")
        stdout.contains("OBSERVED_ENABLED_FALSE=true")

        and: "the reader never observed a NullPointerException — the atomic publish never published a null graph"
        stdout.contains("OBSERVED_NPE=false")

        and: "the reader observed no other throwable type — the hot-swap path raises no unexpected exceptions"
        stdout.contains("OBSERVED_OTHER_THROWABLE=false")
    }

    private static List runProbe(Class<?> probeClass, String... args) {
        def javaBin = new File(System.getProperty("java.home"), "bin/java").absolutePath
        def classpath = System.getProperty("java.class.path")
        def command = [javaBin, "-cp", classpath, probeClass.name] + (args as List)
        def process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        def stdout = process.inputStream.getText("UTF-8")
        int exitCode = process.waitFor()
        return [exitCode, stdout]
    }
}
