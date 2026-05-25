plugins {
    id("org.libprunus.build-logic")

    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":libprunus-core"))
    implementation(libs.byte.buddy)
    implementation(libs.byte.buddy.gradle.plugin)
    implementation(libs.spotless.plugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.logback.classic)
    testImplementation(libs.cfr)
}

gradlePlugin {
    plugins {
        create("libprunusCorePlugin") {
            id = "org.libprunus.libprunus-core-plugin"
            implementationClass = "org.libprunus.core.plugin.LibprunusCorePlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
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
