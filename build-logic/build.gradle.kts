plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.cyclonedx.plugin)
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.license.report.plugin)
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
