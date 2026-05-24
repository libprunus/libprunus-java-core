pluginManagement {
    includeBuild("build-logic")
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
