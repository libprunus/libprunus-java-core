package org.libprunus.spring.server.plugin

import io.spring.gradle.dependencymanagement.DependencyManagementPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import spock.lang.Specification

class LibprunusSpringCorePluginSpec extends Specification {

    def "apply registers spring boot dependency management and core plugin contracts"() {
        given: "a fresh project and the plugin under test"
        def project = ProjectBuilder.builder().withName("libprunus-spring-core-plugin-spec").build()
        def plugin = new LibprunusSpringCorePlugin()

        when: "the plugin is applied to the project"
        plugin.apply(project)

        then: "spring boot and dependency management plugins are applied"
        project.plugins.hasPlugin(SpringBootPlugin)
        project.plugins.hasPlugin(DependencyManagementPlugin)

        and: "core plugin contracts are transitively available"
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)

        and: "the implementation dependency includes libprunus-spring-server"
        def implementationDependencies = project.configurations.getByName("implementation").dependencies
        implementationDependencies.find {
            it.group == "org.libprunus" && it.name == "libprunus-spring-server"
        } != null
    }
}
