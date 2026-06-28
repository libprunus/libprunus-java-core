rootProject.name = "example-thymeleaf"

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
        // node-gradle would self-register this repo, which FAIL_ON_PROJECT_REPOS
        // rejects; declare it centrally and set distBaseUrl = null in
        // build.gradle.kts so the plugin resolves Node from here.
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
    }
}
