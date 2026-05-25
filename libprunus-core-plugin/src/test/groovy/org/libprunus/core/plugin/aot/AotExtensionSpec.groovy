package org.libprunus.core.plugin.aot

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Subject

class AotExtensionSpec extends Specification {

    Project project
    @Subject
    AotExtension extension

    def setup() {
        project = ProjectBuilder.builder().withName("aot-extension-spec").build()
        extension = project.objects.newInstance(AotExtension)
    }

    def "enabled defaults to false"() {
        expect:
        extension.enabled.get() == false
    }

    def "enabled reflects the value provided by the caller"() {
        when:
        extension.enabled.set(true)

        then:
        extension.enabled.get() == true
    }

    def "mode defaults to APPLICATION"() {
        expect:
        extension.mode.get() == AotMode.APPLICATION
    }

    def "mode reflects the value provided by the caller"() {
        when:
        extension.mode.set(AotMode.LIBRARY)

        then:
        extension.mode.get() == AotMode.LIBRARY
    }

    def "logRegistryClass has no convention default"() {
        expect:
        !extension.logRegistryClass.present
    }

    def "logRegistryClass reflects the value provided by the caller"() {
        when:
        extension.logRegistryClass.set("com.example.Registry")

        then:
        extension.logRegistryClass.get() == "com.example.Registry"
    }

    def "enabledInApplicationMode is true only when enabled and mode is APPLICATION"() {
        given:
        extension.enabled.set(enabled)
        extension.mode.set(mode)

        expect:
        extension.enabledInApplicationMode.get() == expected

        where:
        enabled | mode                || expected
        true    | AotMode.APPLICATION || true
        true    | AotMode.LIBRARY     || false
        false   | AotMode.APPLICATION || false
        false   | AotMode.LIBRARY     || false
    }

    def "enabledInLibraryMode is true only when enabled and mode is LIBRARY"() {
        given:
        extension.enabled.set(enabled)
        extension.mode.set(mode)

        expect:
        extension.enabledInLibraryMode.get() == expected

        where:
        enabled | mode                || expected
        true    | AotMode.APPLICATION || false
        true    | AotMode.LIBRARY     || true
        false   | AotMode.APPLICATION || false
        false   | AotMode.LIBRARY     || false
    }

    def "enabledInApplicationMode follows later changes to enabled and mode"() {
        given:
        extension.enabled.set(false)
        extension.mode.set(AotMode.LIBRARY)
        def provider = extension.enabledInApplicationMode

        when:
        extension.enabled.set(true)
        extension.mode.set(AotMode.APPLICATION)

        then:
        provider.get() == true
    }
}
