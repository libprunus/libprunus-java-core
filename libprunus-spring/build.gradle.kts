plugins {
    id("org.libprunus.build-logic")
    `maven-publish`
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(project(":libprunus-core"))
    api(libs.spring.boot.autoconfigure)

    compileOnly(libs.jackson.databind)
    compileOnly(libs.spring.boot.jackson)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.spring.boot.jackson)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
