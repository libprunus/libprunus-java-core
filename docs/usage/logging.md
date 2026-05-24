# Logging

libprunus rewrites application bytecode at build time so that two related concerns — **method entry/exit logging** and **POJO `toString()` rendering** — are handled by inlined, generated call sites instead of reflection or AOP proxies. Both concerns are configured by annotations on a single `@LogRegistry` class.

This document covers:

1. Configuring the Gradle plugin
2. Authoring the registry class
3. Method logging
4. POJO `toString()` rewriting
5. Cross-cutting controls (`@MaxMessageLength`, sensitive-data annotations)
6. AOT modes: `APPLICATION` vs `LIBRARY`
7. Runtime behavior

---

## 1. Gradle plugin configuration

AOT is opt-in. The `libprunus-core-plugin` extension is rooted at the `prunus` block; enable AOT by flipping `enabled` and pointing `logRegistryClass` at a `@LogRegistry` class:

```kotlin
prunus {
    aot {
        enabled = true                                          // default: false
        mode = org.libprunus.core.plugin.aot.AotMode.APPLICATION // default: APPLICATION
        logRegistryClass = "com.example.AppLoggingConvention"
    }
}
```

| Property           | Type      | Default       | Purpose                                                                                                       |
| ------------------ | --------- | ------------- | ------------------------------------------------------------------------------------------------------------- |
| `enabled`          | `Boolean` | `false`       | Master switch. When `false`, no AOT tasks run on this source set.                              |
| `mode`             | `AotMode` | `APPLICATION` | Selects the artifact-shape (see [section 6](#6-aot-modes-application-vs-library)).             |
| `logRegistryClass` | `String`  | unset         | Fully-qualified name of the `@LogRegistry` class to read. Required whenever `enabled = true`.  |

Setting `enabled = true` without a non-blank `logRegistryClass` fails at configuration time. Leaving `enabled = false` skips AOT entirely and ignores any value in `logRegistryClass`.

---

## 2. The registry class

A registry is any type annotated with `@LogRegistry` — a declaration site whose annotations drive routing and rendering for the entire project.

```java
package com.example;

import org.libprunus.core.log.annotation.LogRegistry;

@LogRegistry
public final class AppLoggingConvention {
    private AppLoggingConvention() {}
}
```

Selection is external: at build time the plugin reads exactly the one class named by `prunus.aot.logRegistryClass`. Multiple registry classes are never merged.

---

## 3. Method logging

`@MethodLoggingProfile` routes selected classes into the method-logging code path. Each public instance method on a matched class receives an entry log record before its body runs and an exit record after it returns.

A few categories are filtered out even when their declaring class matches a profile:

- Constructors, static methods, and abstract/native/bridge/synthetic methods.
- `Object`-inherited methods (`toString`, `equals`, `hashCode`, `wait`, `notify`, ...), even when overridden on a matched class.
- Anything annotated `@AutomatedProcessingIgnore` — on the class (excludes every method) or on a single method. The annotation is declared-only: it does not propagate to subclasses or overrides.

The annotation is `@Repeatable` (container: `@MethodLoggingProfiles`), so multiple profiles can live on the same registry class without an explicit wrapper.

```java
@LogRegistry
@MethodLoggingProfile(
        includePackages = {"com.example.web"},
        includeClassSuffixes = {"Controller"},
        fields = {"userId", "traceId"},
        entryLevel = LogLevel.DEBUG,
        exitLevel = LogLevel.DEBUG)
@MethodLoggingProfile(
        includePackages = {"com.example.app"},
        includeClassSuffixes = {"Service"},
        fields = {"traceId"},
        entryLevel = LogLevel.INFO,
        exitLevel = LogLevel.INFO)
public final class AppLoggingConvention {
    private AppLoggingConvention() {}
}
```

### Matching rules

A profile applies to a class only when **all** of the following are true (blank entries are stripped first):

- `includePackages` is non-empty and the class FQN satisfies at least one prefix rule.
- `excludePackages` does not match the class FQN.
- `includeClassSuffixes` is non-empty and the simple class name ends with one of the listed suffixes.

**No overlap.** If two `@MethodLoggingProfile`s could match the same class, configuration processing fails at build time (declaration order is irrelevant). To express "broad parent + dedicated subpackage", combine `includePackages` with `excludePackages`:

```java
@MethodLoggingProfile(
        includePackages = {"com.example"},
        excludePackages = {"com.example.web"},   // carved out below
        includeClassSuffixes = {"Service"},
        entryLevel = LogLevel.INFO,
        exitLevel = LogLevel.INFO)
@MethodLoggingProfile(
        includePackages = {"com.example.web"},
        includeClassSuffixes = {"Service"},
        entryLevel = LogLevel.DEBUG,
        exitLevel = LogLevel.DEBUG)
```

### Log levels

`entryLevel` and `exitLevel` accept any `LogLevel` (default `INFO` for both). Use `LogLevel.OFF` on either to suppress that direction independently.

### Contextual fields: `@MethodLoggingField`

`fields` references named extractors declared on the registry class. Each extractor is a method annotated with `@MethodLoggingField`:

```java
@LogRegistry
@MethodLoggingProfile(
        includePackages = {"com.example"},
        includeClassSuffixes = {"Controller"},
        fields = {"userId", "traceId"})
public final class AppLoggingConvention {

    private AppLoggingConvention() {}

    @MethodLoggingField("userId")
    public static String userId() {
        return MDC.get("userId");
    }

    @MethodLoggingField("traceId")
    public static String traceId() {
        return MDC.get("traceId");
    }
}
```

Constraints (any violation fails build):

- the method is `public`
- the method is `static`
- zero parameters
- non-`void` return type (primitives allowed)
- `value()` is unique within the registry
- the enclosing `@LogRegistry` class is itself `public`

Field names referenced by `fields` must exist as declared `@MethodLoggingField` methods on the same registry class. Inherited extractors from a superclass are **not** discovered; reference them via an explicit delegating method declared on the registry itself.

---

## 4. POJO `toString()` rewriting

`@ToStringProfile` routes selected classes into `toString()` rewriting. Like `@MethodLoggingProfile`, it is `@Repeatable` and uses the same package/suffix matching grammar.

```java
@ToStringProfile(
        includePackages = {"com.example"},
        includeClassSuffixes = {"Dto", "Request", "Response"})
```

Matching semantics, blank-entry handling, and the no-overlap rule are identical to `@MethodLoggingProfile`. Classes that match no `@ToStringProfile` are not rewritten.

The rendered output uses the simple class name as prefix: `User{id=1, name=...}`.

### Direct `toString()` whitelist

Only fields whose value type is in the effective whitelist may call their original `toString()` during rendering. All other values fall back to a structural rendering (class name plus identity) so a misbehaving `toString()` cannot blow up a log line.

The effective whitelist is the union of two layers:

1. **Framework-reserved built-ins** — always honored regardless of user configuration. The reserved set is exposed as `DirectToStringWhitelist.CORE_BUILTIN` and contains nine entries: `CharSequence`, `Number`, `Boolean`, `Character`, `Enum`, `TemporalAccessor`, `UUID`, `Date`, `Class`.
2. **User-declared additions** — optional types listed via `@DirectToStringWhitelist` on the registry. If the annotation is omitted, the effective whitelist is exactly the reserved set above; you do not need to re-list those nine entries.

Declare additional types on the registry like this:

```java
@DirectToStringWhitelist({
    MyDomainId.class,
    InetAddress.class
})
```

Matching is hierarchy-aware: `toString()` is allowed if the value's concrete type, any superclass, or any interface matches an entry in the effective whitelist.

---

## 5. Cross-cutting controls

### Message length: `@MaxMessageLength`

`@MaxMessageLength(int)` caps the **total** length of a single rendered log line, measured across all arguments together. When the cap would be exceeded the renderer appends a marker (`...` and possibly a richer audit form) and stops.

Value rules:

- Negative: fails compilation.
- `0`–`15`: accepted, normalized to `16`.
- `>= 16`: strict upper bound on the total rendered message length.
- `> 1_048_576` (1 MB): fails compilation.

Default if the annotation is absent: `512`.

The bound is **strict**: `builder.length() <= maxMessageLength` always holds. Marker text (`...`, `...[SOE]`, etc.) is non-authoritative — read `StringBuilderWithContext.isTruncated()` if your code needs to know whether a render was cut.

### Sensitive data: `@Sensitive`, `@DoNotLog`, `@DoLog`

Three zero-argument annotations control how individual targets (types, methods, fields, parameters) appear in log output:

| Annotation   | Effect                                                                                                |
| ------------ | ----------------------------------------------------------------------------------------------------- |
| `@Sensitive` | Mask the target with a placeholder. The original value is replaced with a fixed sentinel.             |
| `@DoNotLog`  | Suppress the target entirely; no slot is rendered for it in the log entry.                            |
| `@DoLog`     | Render the target in the clear, overriding any outer `@Sensitive` declared higher up the chain.       |

```java
public class PaymentRequest {
    private final String orderId;

    @Sensitive
    private final String cardNumber;

    @DoNotLog
    private final String fullPin;
    // ...
}
```

The three annotations are mutually exclusive on a single target; declaring more than one fails at build time.

Resolution follows nearest-declaration-first semantics along the inheritance chain. Within a single declaring type the priority order is parameter > method > type. Across the inheritance chain the declaring type's own annotations beat any ancestor's, and the search stops at the nearest layer that carries any decision. If a single inheritance layer contains multiple ancestors with conflicting decisions (for example, one ancestor declares `@Sensitive` and another declares `@DoNotLog` for the same parameter), configuration processing fails at build time with `Conflicting sensitivity declarations for ...`. When no annotation from this family is present anywhere in the resolved chain, the target is rendered in the clear.

---

## 6. AOT modes: `APPLICATION` vs `LIBRARY`

`prunus.aot.mode` selects between two artifact shapes:

| Mode          | When to use                            | What the plugin produces                                                                                                                                                                  |
| ------------- | -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `APPLICATION` | Executable applications (default).     | Bytecode rewriting + a generated callsite-binding class + a packaged provider conflict check. The application JAR is self-contained and uses the registry it was built against. |
| `LIBRARY`     | Reusable libraries.                    | Bytecode rewriting + a `whitelist.txt` resource embedded in the artifact, but **no** callsite binding. The downstream application supplies the final binding and merges whitelists. |

A library project that intends to ship rewritten classes must use `LIBRARY`. An executable application that consumes one or more `LIBRARY` artifacts uses `APPLICATION`; the application's binding aggregates the libraries' whitelists at build time.

---

## 7. Runtime behavior

### SLF4J integration

Generated call sites obtain a `Logger` via SLF4J `LoggerFactory`. The logger name defaults to the rewritten class's name. Any SLF4J-compatible backend (Logback, Log4j 2 over SLF4J, etc.) consumes these records.

`LogLevel.isEnabled(Logger)` is checked at runtime before formatting any arguments, so a method whose entry level is below the active threshold pays nothing more than the level check.

### Failure isolation

A logging failure never surfaces into application code. Rendering or appender errors are captured by an internal rate-limited reporter that writes to `System.err`; failures beyond the rate limit are dropped and their count is summarized alongside the next successful report. `VirtualMachineError`s other than `StackOverflowError` are rethrown, since silently swallowing them would mask fatal JVM state.

### Runtime configuration

The only runtime-mutable flag is the master switch — the `enabled` field of `LogRuntimeConfig` — read by `LogRuntime.isEnabled()` on every call. Compile-time bindings (`globalMaxMessageLength`, type whitelist) are installed once at startup and are **not** affected by any runtime refresh.

**Default path: `ConfigurationRepository`.** Constructing it installs its live reference into `LogRuntime`; `refresh(...)` takes effect immediately on subsequent reads.

```java
ConfigurationRepository repo = new ConfigurationRepository(new CoreRuntimeConfig(new LogRuntimeConfig(true)));
repo.refresh(new CoreRuntimeConfig(new LogRuntimeConfig(false)));
```

**Spring Boot.** `libprunus-spring` autoconfigures the repository wired to `CoreRuntimeProperties`. Set the master switch via the `libprunus.log.enabled` application property (default `true`); refresh through Spring's normal configuration channels.

**Custom path.** Applications with their own config plumbing call `LogRuntime.linkToDataPlane(myAtomicReference)` directly and publish updates via `myAtomicReference.set(...)`. When nothing is linked, the runtime falls back to its built-in default (enabled).

---

## Complete example

```java
package com.example;

import org.libprunus.core.log.annotation.LogRegistry;
import org.libprunus.core.log.annotation.MaxMessageLength;
import org.libprunus.core.log.annotation.MethodLoggingField;
import org.libprunus.core.log.annotation.MethodLoggingProfile;
import org.libprunus.core.log.annotation.ToStringProfile;
import org.libprunus.core.log.runtime.LogLevel;
import org.slf4j.MDC;

@LogRegistry
@MethodLoggingProfile(
        includePackages = {"com.example.web"},
        includeClassSuffixes = {"Controller"},
        fields = {"userId", "traceId"},
        entryLevel = LogLevel.DEBUG,
        exitLevel = LogLevel.DEBUG)
@MethodLoggingProfile(
        includePackages = {"com.example.app"},
        includeClassSuffixes = {"Service"},
        fields = {"traceId"},
        entryLevel = LogLevel.INFO,
        exitLevel = LogLevel.INFO)
@ToStringProfile(
        includePackages = {"com.example"},
        includeClassSuffixes = {"Dto", "Request", "Response"})
@MaxMessageLength(512)
public final class AppLoggingConvention {

    private AppLoggingConvention() {}

    @MethodLoggingField("userId")
    public static String userId() {
        return MDC.get("userId");
    }

    @MethodLoggingField("traceId")
    public static String traceId() {
        return MDC.get("traceId");
    }
}
```

```kotlin
prunus {
    aot {
        enabled = true
        logRegistryClass = "com.example.AppLoggingConvention"
    }
}
```
