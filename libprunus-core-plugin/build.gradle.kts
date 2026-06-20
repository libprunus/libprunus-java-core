import org.gradle.api.tasks.WriteProperties

plugins {
    id("org.libprunus.build-logic")

    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":libprunus-core"))
    implementation(libs.byte.buddy)
    implementation(libs.byte.buddy.gradle.plugin)
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.pitest.gradle.plugin)
    implementation(libs.spotless.plugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.logback.classic)
    testImplementation(libs.cfr)
}

val generateToolVersions by tasks.registering(WriteProperties::class) {
    property("errorprone-core", libs.versions.errorprone.core.get())
    property("nullaway", libs.versions.nullaway.get())
    property("jspecify", libs.versions.jspecify.get())
    property("spock", libs.versions.spock.get())
    property("groovy", libs.versions.groovy.get())
    property("pitest", libs.versions.pitest.core.get())
    property("pitest-junit5", libs.versions.pitest.junit5.get())
    destinationFile.set(layout.buildDirectory.file("generated/tool-versions/libprunus-tool-versions.properties"))
}

tasks.processResources {
    from(generateToolVersions)
}

gradlePlugin {
    plugins {
        create("libprunusCorePlugin") {
            id = "org.libprunus.libprunus-core-plugin"
            implementationClass = "org.libprunus.core.plugin.LibprunusCorePlugin"
        }
    }
}

tasks.register<JavaExec>("inspectAotBytecode") {
    group = "verification"
    description =
        "Dumps AOT-transformed class files, javap disassembly, and cfr-decompiled sources to build/aot-inspection for human review"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.libprunus.core.plugin.aot.log.testutil.AotBytecodeInspector")
    args(layout.buildDirectory.get().dir("aot-inspection").asFile.absolutePath)
}
