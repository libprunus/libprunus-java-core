plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.spring.boot.bom))

    constraints {
        api(project(":libprunus-core"))
        api(project(":libprunus-spring"))
        api(project(":libprunus-spring-server"))
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
    repositories {
        mavenLocal()
    }
}



