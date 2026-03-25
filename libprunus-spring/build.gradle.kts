plugins {
    id("org.libprunus.build-logic")
    `maven-publish`
}

dependencies {
    api(project(":libprunus-core"))
    api(libs.spring.boot.autoconfigure)

    testImplementation(libs.spring.boot.starter.test)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
