package org.libprunus.core.log.runtime

import spock.lang.Specification

class LogRuntimeCallsiteBindingBootstrapIntegrationSpec extends Specification {

    def "callsite invocation publishes binding into runtime state in a fresh JVM"() {
        when:
        def (exitCode, stdout) = runProbe(BindingPublishingCallsiteProbe)

        then:
        exitCode == 0
        stdout.contains("MAX_LENGTH=2048")
        stdout.contains("WHITELISTED_INTEGER=true")
        stdout.contains("WHITELISTED_STRING=false")
    }

    def "callsite written at CallsiteBindingProtocol RESOURCE_PATH is the exact coordinate LogRuntime resolves in a fresh JVM"() {
        when: "the probe writes the callsite resource at CallsiteBindingProtocol.RESOURCE_PATH and asks LogRuntime to resolve it"
        def (exitCode, stdout) = runProbe(CallsiteBindingProtocolCoordinateProbe)

        then: "the probe exits cleanly and reports the exact protocol coordinate it wrote at"
        exitCode == 0
        stdout.contains("RESOURCE_PATH=" + CallsiteBindingProtocol.RESOURCE_PATH)

        and: "LogRuntime resolved that exact coordinate and the probe's bind() ran — proving producer (protocol constant) and consumer (LogRuntime CALLSITE_BINDING_RESOURCE) are bound to one and the same path"
        stdout.contains("BOUND_OK=true")
        stdout.contains("MAX_LENGTH=4096")
    }

    def "fresh JVM resolves callsite class name when the META-INF resource contains whitespace around the class name"() {
        when:
        def (exitCode, stdout) = runProbe(CallsiteResourceWhitespaceProbe, mode)

        then: "the probe ran cleanly — strip() handled the whitespace and Class.forName succeeded"
        exitCode == 0

        and: "the resolution path actually invoked the probe's bind() — the binding values are the probe's, not DEFAULT"
        stdout.contains("MODE=" + mode)
        stdout.contains("MAX_LENGTH=2048")
        stdout.contains("WHITELISTED_INTEGER=true")

        where: "real-world build tools commonly emit one of these whitespace variants in META-INF resource files"
        mode << ["trailing-lf", "trailing-crlf", "leading-spaces"]
    }

    private static List runProbe(Class<?> probeClass, String... probeArgs) {
        def javaBin = new File(System.getProperty("java.home"), "bin/java").absolutePath
        def classpath = System.getProperty("java.class.path")
        def command = [javaBin, "-cp", classpath, probeClass.name] + (probeArgs as List)
        def process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        def stdout = process.inputStream.getText("UTF-8")
        int exitCode = process.waitFor()
        return [exitCode, stdout]
    }
}
