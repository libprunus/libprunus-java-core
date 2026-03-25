plugins {
    id("org.libprunus.build-logic")
    `maven-publish`
}

dependencies {
    api(project(":libprunus-spring"))
    api(libs.spring.boot.starter.web)
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
