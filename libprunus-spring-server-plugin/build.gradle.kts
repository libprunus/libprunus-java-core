plugins {
    id("org.libprunus.build-logic")

    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":libprunus-core-plugin"))
    implementation(libs.spring.dependency.management.plugin)
    implementation(libs.spring.boot.plugin)
}

gradlePlugin {
    plugins {
        create("libprunusSpringCorePlugin") {
            id = "org.libprunus.libprunus-spring-core-plugin"
            implementationClass = "org.libprunus.spring.server.plugin.LibprunusSpringCorePlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
