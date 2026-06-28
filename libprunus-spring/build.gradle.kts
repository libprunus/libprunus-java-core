plugins {
    id("org.libprunus.build-logic")
    `maven-publish`
}

dependencies {
    api(project(":libprunus-core"))

    compileOnly(platform(libs.spring.boot.bom))
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.spring.boot.jackson)
    compileOnly(libs.spring.webmvc)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.spring.boot.jackson)
    testImplementation(libs.spring.boot.starter.web)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
