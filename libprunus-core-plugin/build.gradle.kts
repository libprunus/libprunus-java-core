plugins {
    id("org.libprunus.build-logic")

    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":libprunus-core"))
    implementation(libs.byte.buddy)
    implementation(libs.byte.buddy.gradle.plugin)
    implementation(libs.spotless.plugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.logback.classic)
}

gradlePlugin {
    plugins {
        create("libprunusCorePlugin") {
            id = "org.libprunus.libprunus-core-plugin"
            implementationClass = "org.libprunus.core.plugin.LibprunusCorePlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
