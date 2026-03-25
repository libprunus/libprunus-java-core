plugins {
    alias(libs.plugins.libprunus.spring.core)
}

dependencies {
    implementation(platform(libs.libprunus.bom))
    testImplementation(enforcedPlatform(libs.libprunus.bom))
}
