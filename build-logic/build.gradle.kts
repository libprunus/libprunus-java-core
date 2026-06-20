plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.pitest.gradle.plugin)
    implementation(libs.sonarqube.plugin)
    implementation(libs.spotless.plugin)
}

gradlePlugin {
    plugins {
        create("buildLogic") {
            id = "org.libprunus.build-logic"
            implementationClass = "org.libprunus.buildlogic.BuildLogicPlugin"
        }
    }
}
