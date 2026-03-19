plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.spock.bom))

    constraints {
        api(libs.spock.core)
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



