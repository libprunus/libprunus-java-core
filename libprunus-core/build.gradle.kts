plugins {
    id("org.libprunus.build-logic")
    `maven-publish`
}

dependencies {
    api(libs.slf4j.api)
    testImplementation(libs.logback.classic)
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
