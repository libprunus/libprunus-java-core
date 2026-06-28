pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("com.autonomousapps.build-health") version "3.16.0"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "libprunus-java-core"

include("libprunus-bom")
include("libprunus-core")
include("libprunus-core-plugin")
include("libprunus-spring")
