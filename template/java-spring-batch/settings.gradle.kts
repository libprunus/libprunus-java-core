rootProject.name = "example-batch"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Single source of truth for repositories: FAIL_ON_PROJECT_REPOS rejects any
    // declared in a project or convention plugin.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
