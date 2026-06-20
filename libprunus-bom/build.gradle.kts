plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        api(project(":libprunus-core"))
        api(project(":libprunus-spring"))
        api(libs.spock.core)
        api(libs.groovy.core) {
            version {
                strictly(libs.versions.groovy.get())
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])

            // Suppress POM metadata warnings for Maven-incompatible version formats (e.g. "2.4-groovy-4.0")
            suppressPomMetadataWarningsFor("apiElements")
            suppressPomMetadataWarningsFor("runtimeElements")
        }
    }
}



