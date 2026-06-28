import com.github.gradle.node.npm.task.NpmTask

plugins {
    alias(libs.plugins.libprunus.core.plugin)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.node.gradle)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.libprunus.bom))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.libprunus.spring)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.h2)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.jdbc.test)
}

prunus {
    // libprunus toolchain + quality gates (JaCoCo coverage, PIT mutation). Tune per
    // project, but keep them strict enough to matter — tests grow with the code.
    javaBuild {
        targetJavaVersion = 25
        instructionCoverageThreshold = 0.7
        lineCoverageThreshold = 0.7
        branchCoverageThreshold = 0.7
        spockEnabled = false
        pitestEnabled = true
        mutationThreshold = 50
    }
    // Build-time AOT bytecode rewriting; logRegistryClass names the @LogRegistry
    // class that declares which classes get method logging / toString rewriting.
    aot {
        enabled = true
        logRegistryClass = "com.example.backend.AppLoggingConvention"
    }
}

node {
    // Pin Node/npm so the Prettier static check is reproducible regardless of
    // what's on the developer/CI PATH.
    version = "22.12.0"
    download = true
    npmInstallCommand = "ci"
    // Node distribution repo is declared centrally in settings.gradle.kts
    // (FAIL_ON_PROJECT_REPOS); null stops the plugin registering its own.
    distBaseUrl = null
}

// The view layer (Thymeleaf templates + static assets) lives in the conventional
// Spring location and is served straight from the classpath — no module boundary
// to cross, so no variant-aware sharing. Prettier checks it in place: inputs are
// declared for up-to-date skipping, it owns no output, and it depends on nothing
// the jar build produces, so it stays a pure verification gate.
val frontendFormatCheck = tasks.register<NpmTask>("frontendFormatCheck") {
    group = "verification"
    description = "Checks formatting of templates & static assets with Prettier."
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "format:check")
    inputs.dir(layout.projectDirectory.dir("src/main/resources/templates"))
    inputs.dir(layout.projectDirectory.dir("src/main/resources/static"))
    inputs.files("package.json", "package-lock.json", ".prettierrc.json", ".prettierignore")
        .withPropertyName("frontendFormatInputs")
    outputs.upToDateWhen { true }
}

val frontendFormat = tasks.register<NpmTask>("frontendFormat") {
    group = "formatting"
    description = "Formats templates & static assets with Prettier."
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "format")
}

tasks.named("check") { dependsOn(frontendFormatCheck) }

// `format` mirrors `check` on the write side: one entry point that applies
// Spotless (Java/Kotlin) and Prettier (templates & static assets).
tasks.register("format") {
    group = "formatting"
    description = "Applies Spotless (Java/Kotlin) and Prettier (templates & static assets)."
    dependsOn("spotlessApply", frontendFormat)
}

dependencyAnalysis {
    structure {
        // Treat each ecosystem starter as a bundle so the analysis does not
        // advise exploding a coarse-grained starter into its transitives.
        bundle("spring") {
            includeGroup("org.springframework.boot")
            includeGroup("org.springframework")
        }
        bundle("junit") { includeGroup("org.junit.jupiter") }
    }
}
