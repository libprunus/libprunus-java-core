# libprunus

An opinionated convention framework for Java modules. Current conventions:

- A coherent Java build profile (toolchain + JaCoCo coverage gates).
- Annotation-driven, build-time bytecode generation for structured method-entry/exit logging and safe-by-default `toString()` rendering.

## Modules

| Module                  | Role                                                            |
| ----------------------- | --------------------------------------------------------------- |
| `libprunus-core`        | The core Java library — annotations and runtime support.        |
| `libprunus-core-plugin` | Gradle build-time plugin serving `libprunus-core`.              |
| `libprunus-spring`      | Spring Boot integration for `libprunus-core`.                   |
| `libprunus-bom`         | Maven BOM aligning module versions.                             |

## Getting started

Apply the plugin and pull in the core library through the BOM:

```kotlin
plugins {
    id("org.libprunus.libprunus-core-plugin")
}

dependencies {
    implementation(platform("org.libprunus:libprunus-bom:<version>"))
    implementation("org.libprunus:libprunus-core")
}
```

That alone gives the module the Java build conventions described in [Java toolchain & coverage](#java-toolchain--coverage) — no further configuration is required.

### Enable AOT (optional)

AOT bytecode rewriting is off by default. Turn it on only when you want method logging or `toString` rewriting:

```kotlin
prunus {
    aot {
        enabled = true
        logRegistryClass = "com.example.AppLoggingConvention"
    }
}
```

`logRegistryClass` must point to a `@LogRegistry`-annotated class. The class itself is just a declaration site — annotations placed on it drive method logging and `toString` rewriting for the whole module:

```java
package com.example;

import org.libprunus.core.log.annotation.LogRegistry;

@LogRegistry
public final class AppLoggingConvention {
    private AppLoggingConvention() {}
}
```

See [docs/usage/logging.md](docs/usage/logging.md) for the full set of registry annotations (`@MethodLoggingProfile`, `@ToStringProfile`, `@MethodLoggingField`, `@DirectToStringWhitelist`, `@MaxMessageLength`, `@Sensitive`, `@DoNotLog`, `@DoLog`).

## Java toolchain & coverage

The `javaBuild { ... }` block configures the Java toolchain and JaCoCo coverage gates. All properties have conventions, so the block is optional.

```kotlin
prunus {
    javaBuild {
        targetJavaVersion = 25               // default 25; drives Gradle toolchain languageVersion + javac --release
        instructionCoverageThreshold = 0.9   // default 0.9
        lineCoverageThreshold = 0.9          // default 0.9
        branchCoverageThreshold = 0.9        // default 0.9
    }
}
```

`targetJavaVersion` selects the toolchain Gradle will provision (auto-download per your Gradle settings) and also sets the `--release` flag on every `JavaCompile` task. The three coverage thresholds wire into `JacocoCoverageVerification` and run as part of `check`.

See [docs/usage/](docs/usage/) for AOT method logging, POJO rendering, and Spring Boot autoconfiguration.

## Build conventions

Applying `org.libprunus.libprunus-core-plugin` is the single entry point. Downstream modules inherit the following conventions without any further wiring.

### Gradle plugins applied on the consumer's behalf

| Plugin | Role |
| --- | --- |
| `java-library` | Java library layout, plus a sources jar and a javadoc jar. |
| `jacoco` | Coverage instrumentation and verification (see [Java toolchain & coverage](#java-toolchain--coverage)). |
| `com.diffplug.spotless` | Format gate. |
| `net.bytebuddy.byte-buddy-gradle-plugin` | Drives AOT bytecode rewriting when AOT is enabled. |

### Conventions set on every module

- **Java compile** — UTF-8, `-parameters`, `-Werror`, `--release <targetJavaVersion>`.
- **Test** — JUnit Platform with `org.junit.jupiter:junit-jupiter`, finalized by the JaCoCo report.
- **Coverage gates** — `JacocoCoverageVerification` runs as part of `check`.
- **Format** — Palantir Java format on `src/**/*.java`, enforced by Spotless.

## Documentation

- [docs/usage/](docs/usage/) — user-facing references (AOT logging, runtime contracts, wiring).
- [docs/contributing/](docs/contributing/) — contributor rules.
