plugins {
    alias(libs.plugins.libprunus.core.plugin)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.analysis)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.libprunus.bom))
    implementation(libs.spring.boot.starter.batch.jdbc)
    implementation(libs.libprunus.core)
    runtimeOnly(libs.libprunus.spring)
    runtimeOnly(libs.h2)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.batch.test)
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
        logRegistryClass = "com.example.batch.AppLoggingConvention"
    }
}

dependencyAnalysis {
    structure {
        // Treat each ecosystem starter as a bundle so the analysis does not
        // advise exploding a coarse-grained starter into its transitives.
        bundle("spring") {
            includeGroup("org.springframework.boot")
            includeGroup("org.springframework")
            includeGroup("org.springframework.batch")
        }
        bundle("junit") { includeGroup("org.junit.jupiter") }
    }
}
