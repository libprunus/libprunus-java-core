package org.libprunus.spring.core.plugin

import io.spring.gradle.dependencymanagement.DependencyManagementPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import spock.lang.Specification

class LibprunusSpringCorePluginSpec extends Specification {

    def "apply registers spring boot dependency management and core plugin contracts"() {
        given: "a fresh project and plugin instance"
        def project = ProjectBuilder.builder().withName("libprunus-spring-core-plugin-spec").build()
        def plugin = new LibprunusSpringCorePlugin()

        when: "the plugin is applied"
        plugin.apply(project)

        then: "spring boot and dependency management plugins are present"
        project.plugins.hasPlugin(SpringBootPlugin)
        project.plugins.hasPlugin(DependencyManagementPlugin)

        and: "core plugin contracts are transitively applied"
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)

        and: "the runtime starter stack is provided through libprunus-spring-server"
        def implementationDependencies = project.configurations.getByName("implementation").dependencies
        implementationDependencies.find {
            it.group == "org.libprunus" && it.name == "libprunus-spring-server"
        } != null
    }
}
