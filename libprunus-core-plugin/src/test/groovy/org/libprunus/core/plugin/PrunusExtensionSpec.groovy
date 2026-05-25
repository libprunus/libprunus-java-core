package org.libprunus.core.plugin

import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import org.libprunus.core.plugin.aot.AotExtension
import org.libprunus.core.plugin.buildlogic.JavaBuildExtension
import spock.lang.Specification

class PrunusExtensionSpec extends Specification {

    def "ctor newInstance call wires javaBuild and aot to distinct nested extension instances exposed by stable getters"() {
        given:
        def project = ProjectBuilder.builder().withName("prunus-extension-construction").build()

        when:
        def extension = project.objects.newInstance(PrunusExtension)

        then:
        extension.javaBuild instanceof JavaBuildExtension
        extension.aot instanceof AotExtension
        !extension.javaBuild.is(extension.aot)
        extension.javaBuild.is(extension.getJavaBuild())
        extension.aot.is(extension.getAot())
    }

    def "javaBuild action configures the same instance returned by getJavaBuild without touching aot"() {
        given:
        def project = ProjectBuilder.builder().withName("prunus-extension-javabuild-action").build()
        def extension = project.objects.newInstance(PrunusExtension)
        JavaBuildExtension captured = null

        when:
        extension.javaBuild({ JavaBuildExtension it ->
            captured = it
            it.targetJavaVersion.set(21)
        } as Action)

        then:
        captured.is(extension.javaBuild)
        extension.javaBuild.targetJavaVersion.get() == 21
        extension.aot.enabled.get() == false
        extension.aot.logRegistryClass.getOrNull() == null
    }

    def "aot action configures the same instance returned by getAot without touching javaBuild"() {
        given:
        def project = ProjectBuilder.builder().withName("prunus-extension-aot-action").build()
        def extension = project.objects.newInstance(PrunusExtension)
        AotExtension captured = null

        when:
        extension.aot({ AotExtension it ->
            captured = it
            it.enabled.set(true)
            it.logRegistryClass.set("sample.Registry")
        } as Action)

        then:
        captured.is(extension.aot)
        extension.aot.enabled.get() == true
        extension.aot.logRegistryClass.get() == "sample.Registry"
        extension.javaBuild.targetJavaVersion.get() == 25
    }
}
