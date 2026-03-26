package org.libprunus.core.plugin

import java.util.Optional
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import spock.lang.Specification

class LibprunusCorePluginSpec extends Specification {

    def "apply delegates to java build logic and configures expected plugins"() {
        given: "a fresh project and plugin instance"
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-spec").build()
        def libs = Stub(org.gradle.api.artifacts.VersionCatalog) {
            findLibrary(_) >> Optional.empty()
        }
        def catalogs = Stub(org.gradle.api.artifacts.VersionCatalogsExtension) {
            named("libs") >> libs
        }
        project.extensions.add(org.gradle.api.artifacts.VersionCatalogsExtension, "versionCatalogs", catalogs)
        def plugin = new LibprunusCorePlugin()

        when: "the plugin is applied"
        plugin.apply(project)

        then: "core plugin contracts are visible on project plugins and tasks"
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.tasks.findByName("spotlessJava") != null
        project.tasks.findByName("spotlessKotlinGradle") != null
    }

    def "apply works when version catalogs extension is absent"() {
        given: "a fresh project without version catalogs extension"
        def project = ProjectBuilder.builder().withName("libprunus-core-plugin-no-catalog-spec").build()
        def plugin = new LibprunusCorePlugin()

        when: "the plugin is applied"
        plugin.apply(project)

        then: "core plugin contracts are still visible on project plugins and tasks"
        project.plugins.hasPlugin(JacocoPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.tasks.findByName("spotlessJava") != null
        project.tasks.findByName("spotlessKotlinGradle") != null
    }
}
