# Third-Party Dependency Licenses

Generated 2026-06. Versions are governed by the project's `libs.versions.toml` files and the imported Spring Boot BOM.

## Application libraries

| Dependency | License | SPDX |
|---|---|---|
| org.slf4j:slf4j-api | MIT | `MIT` |
| org.jspecify:jspecify | Apache License 2.0 | `Apache-2.0` |
| tools.jackson.core:jackson-databind | Apache License 2.0 | `Apache-2.0` |
| org.springframework.boot:spring-boot-autoconfigure | Apache License 2.0 | `Apache-2.0` |
| org.springframework.boot:spring-boot-jackson | Apache License 2.0 | `Apache-2.0` |
| org.springframework:spring-webmvc | Apache License 2.0 | `Apache-2.0` |

## Build & test tooling

| Dependency | License | SPDX |
|---|---|---|
| net.bytebuddy:byte-buddy | Apache License 2.0 | `Apache-2.0` |
| net.bytebuddy:byte-buddy-gradle-plugin | Apache License 2.0 | `Apache-2.0` |
| com.google.errorprone:error_prone_core | Apache License 2.0 | `Apache-2.0` |
| net.ltgt.gradle:gradle-errorprone-plugin | Apache License 2.0 | `Apache-2.0` |
| com.uber.nullaway:nullaway | MIT | `MIT` |
| com.diffplug.spotless:spotless-plugin-gradle | Apache License 2.0 | `Apache-2.0` |
| info.solidsoft.gradle.pitest:gradle-pitest-plugin | Apache License 2.0 | `Apache-2.0` |
| org.pitest:pitest | Apache License 2.0 | `Apache-2.0` |
| org.pitest:pitest-junit5-plugin | Apache License 2.0 | `Apache-2.0` |
| io.spring.gradle:dependency-management-plugin | Apache License 2.0 | `Apache-2.0` |
| org.springframework.boot:spring-boot-gradle-plugin | Apache License 2.0 | `Apache-2.0` |
| org.sonarsource.scanner.gradle:sonarqube-gradle-plugin | GNU LGPL v3.0 | `LGPL-3.0-only` |
| org.cyclonedx:cyclonedx-gradle-plugin | Apache License 2.0 | `Apache-2.0` |
| com.github.jk1:gradle-license-report | Apache License 2.0 | `Apache-2.0` |
| org.spockframework:spock-core | Apache License 2.0 | `Apache-2.0` |
| org.apache.groovy:groovy | Apache License 2.0 | `Apache-2.0` |
| ch.qos.logback:logback-classic | EPL-2.0 OR LGPL-2.1 | `EPL-2.0 OR LGPL-2.1-only` |
| org.benf:cfr | MIT | `MIT` |
| org.springframework.boot:spring-boot-starter-test | Apache License 2.0 | `Apache-2.0` |
| org.junit.jupiter:junit-jupiter | Eclipse Public License 2.0 | `EPL-2.0` |
| org.junit.platform:junit-platform-launcher | Eclipse Public License 2.0 | `EPL-2.0` |
| org.springframework.boot:spring-boot-starter-web | Apache License 2.0 | `Apache-2.0` |

## Notes

All dependencies are permissively licensed (MIT / Apache-2.0) except the following
weak-copyleft components, all confined to build/test time and not part of the
application runtime:

- `org.junit.jupiter:junit-jupiter`, `org.junit.platform:junit-platform-launcher`
  — Eclipse Public License 2.0; injected into every project as test dependencies.
- `ch.qos.logback:logback-classic` — dual-licensed under EPL-2.0 or LGPL-2.1-only;
  used in test scope only.
- `org.sonarsource.scanner.gradle:sonarqube-gradle-plugin` — LGPL-3.0-only; used
  in build scope only. This Gradle scanner plugin is not affected by SonarSource's
  2024 SSALv1 relicensing, which applies only to the bundled SonarQube analyzers.
