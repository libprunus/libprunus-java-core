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
        spockEnabled = true                  // default false; opt in to Spock (Groovy) tests
        pitestEnabled = false                // default true; opt out of PIT mutation testing
        mutationThreshold = 70               // default 70; min mutation kill %, gate runs in check after test
    }
}
```

`targetJavaVersion` selects the toolchain Gradle will provision (auto-download per your Gradle settings) and also sets the `--release` flag on every `JavaCompile` task. The three coverage thresholds wire into `JacocoCoverageVerification` and run as part of `check`. `spockEnabled` (default `false`) applies the Groovy plugin and adds Spock to `testImplementation`; tests stay JUnit-only otherwise.

`pitestEnabled` (default `true`) wires PIT mutation testing into `check`, running after `test`; the build fails below `mutationThreshold` (default `70`% kill rate). PIT targets `${project.group}.*`, so the consuming module must set a `group`; modules with no mutable production code pass (no-mutations is lenient, not a failure). Opt out with `pitestEnabled = false`.

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
| `net.ltgt.errorprone` | Hosts the NullAway null-safety gate. |
| `info.solidsoft.pitest` | PIT mutation-testing gate, bound to `check` (after `test`). |

### Conventions set on every module

- **Java compile** — UTF-8, `-parameters`, `-Werror`, `--release <targetJavaVersion>`.
- **Test** — JUnit Platform with `org.junit.jupiter:junit-jupiter`, finalized by the JaCoCo report.
- **Coverage gates** — `JacocoCoverageVerification` runs as part of `check`.
- **Format** — Palantir Java format on `src/**/*.java`, enforced by Spotless.
- **Null-safety** — NullAway (Error Prone, JSpecify mode): every production package must be `@NullMarked` (else the build fails), and `@NullMarked` code is null-checked at `error` severity.
- **Mutation testing** — PIT runs as part of `check` (after `test`); the build fails below `mutationThreshold` (default 70%). Requires the module's `group`. Opt out with `pitestEnabled = false`.

### Tool versions passed downstream

These versions are pinned in `gradle/libs.versions.toml` (single source), bundled into the plugin, and injected into the consumer build — so consumers need no version management for them:

| Tool | Version | Role |
| --- | --- | --- |
| `com.google.errorprone:error_prone_core` | 2.38.0 | Error Prone compiler hosting NullAway |
| `com.uber.nullaway:nullaway` | 0.12.15 | Null-safety checks + `RequireExplicitNullMarking` enforcement |
| `org.jspecify:jspecify` | 1.0.0 | `@NullMarked` / `@Nullable` annotations (`api`) |
| `org.spockframework:spock-core` | 2.4-groovy-4.0 | Spock tests (opt-in via `spockEnabled`) |
| `org.apache.groovy:groovy` | 4.0.29 | Groovy for Spock (opt-in) |
| `info.solidsoft.gradle.pitest:gradle-pitest-plugin` | 1.19.0 | Gradle plugin driving PIT |
| `org.pitest:pitest` | 1.25.5 | PIT mutation engine |
| `org.pitest:pitest-junit5-plugin` | 1.2.3 | JUnit Platform / Spock support for PIT |

## Documentation

- [docs/usage/](docs/usage/) — user-facing references (AOT logging, runtime contracts, wiring).
- [docs/contributing/](docs/contributing/) — contributor rules.
