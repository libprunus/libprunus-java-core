package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.libprunus.core.log.runtime.LogLevel;

/**
 * Routing rule that maps classes to a complete method logging configuration.
 *
 * <p>A profile is declared on a {@link LogRegistry} class. A matching profile determines both the
 * additional logging fields and the entry/exit log levels for that class.
 *
 * <p>Profile declaration order has no semantic meaning. If multiple {@link MethodLoggingProfile}
 * declarations can match the same class, that is a configuration error.
 *
 * <p>For parent-package fallback with dedicated subpackage behavior, combine {@link
 * #includePackages()} with {@link #excludePackages()}: define a broad parent prefix in {@code
 * includePackages}, list excluded child prefixes in {@code excludePackages}, and define separate,
 * non-overlapping profiles for the excluded child prefixes.
 *
 * <p>A profile matches a class only when <em>all</em> conditions are true:
 *
 * <ul>
 *   <li>{@link #includePackages()} is non-empty after blank-entry removal, and the class
 *       fully-qualified name satisfies at least one package prefix rule.
 *   <li>{@link #excludePackages()} is checked after blank-entry removal, and the class
 *       fully-qualified name satisfies none of the listed package prefix rules.
 *   <li>{@link #includeClassSuffixes()} is non-empty after blank-entry removal, and the simple class
 *       name ends with at least one of the listed suffixes.
 * </ul>
 *
 * <p>If either condition produces an empty list (omitted, empty array, or all-blank entries), the
 * profile matches no classes.
 *
 * <p>The container annotation {@link MethodLoggingProfiles} is generated automatically by the
 * compiler when multiple profiles are declared.
 *
 * <h2>Eligible methods</h2>
 *
 * <p>Entry/exit logging is applied to {@code public} instance methods of a matched class. The
 * following method shapes are <b>excluded</b>:
 *
 * <ul>
 *   <li>static methods (naming conventions are not special-cased &mdash; a static factory method
 *       named {@code of(...)} follows the static-method rule);
 *   <li>constructors;
 *   <li>non-public methods ({@code protected}, package-private, {@code private});
 *   <li>synthetic methods and bridge methods (including the synthetic method generated for a
 *       lambda expression);
 *   <li>overrides of methods declared on {@link Object} (such as {@code equals},
 *       {@code hashCode}, {@code toString}).
 * </ul>
 *
 * <p>Family annotations ({@link Sensitive}, {@link DoNotLog}, {@link DoLog}) declared on
 * excluded methods or on their parameters are silently ignored at the corresponding scope
 * &mdash; they are not a configuration error.
 *
 * <h2>Per-call emit format</h2>
 *
 * <p>Each call site under this contract emits two lines (subject to the configured {@link #entryLevel()} /
 * {@link #exitLevel()} and the resolved policy from {@link Sensitive} / {@link DoNotLog} /
 * {@link DoLog}):
 *
 * <pre>{@code |> [ENTER] SimpleName.method(p1=v1, p2=v2, ...)
 * |< [EXIT] SimpleName.method(value=returnValue)}</pre>
 *
 * <p>{@code SimpleName} is the simple name of the <em>declaring class</em> of the executing
 * method &mdash; not the runtime receiver class. See {@link Sensitive} for the declaration-class
 * chain rule.
 *
 * <p><b>Why declaring class, not runtime receiver:</b> the family-resolution algorithm (see
 * {@link Sensitive}) operates on the declaration-class chain. The SimpleName reported in
 * ENTER/EXIT must match the class whose annotations governed the resolution, so that a log
 * reader can trace "why was this masked / dropped / passed through" back to a specific
 * source-code declaration. Reporting the runtime receiver class would decouple log identity
 * from policy identity, leaving the reader unable to map a rendered slot back to the annotations
 * that produced it.
 *
 * <p>For <b>void-returning methods</b>, the EXIT line omits the {@code value=} slot entirely:
 * {@code |< [EXIT] SimpleName.method()}. The return-target family resolution does not apply to
 * void methods.
 *
 * <h3>Exceptional control flow</h3>
 *
 * <p>When an instrumented method throws, ENTER is emitted normally before the body executes.
 * If the body throws, the EXIT line is <b>not</b> emitted, and the exception propagates to the
 * caller unchanged. The framework does not catch, wrap, suppress, or log the exception itself
 * &mdash; exceptions are out of band relative to the ENTER/EXIT contract. The only observable
 * side-effect of instrumentation in an exceptional path is the orphan ENTER line.
 *
 * <h3>Per-parameter and return-value rendering</h3>
 *
 * <p>Per-parameter and return-value rendering follows the effective family resolved by the
 * algorithm in {@link Sensitive}:
 *
 * <ul>
 *   <li>{@link Sensitive}: parameter or return value renders as the masked sentinel.
 *   <li>{@link DoNotLog} at parameter scope: the parameter slot is dropped from the ENTER line;
 *       the method still emits ENTER and EXIT.
 *   <li>{@link DoNotLog} at method scope: see {@link DoNotLog} for the per-target rendering
 *       rule and the whole-method rendering optimization.
 *   <li>{@link DoLog} (or no family annotation anywhere in the chain): rendered verbatim.
 * </ul>
 *
 * <h2>Override and super-call chain</h2>
 *
 * <p>Each class's ENTER/EXIT pair is bound to the method as declared on that class. A subclass
 * override is a distinct method declaration; calling the method on a subclass instance emits
 * the override's ENTER/EXIT, not the parent's.
 *
 * <p>When an override body delegates to {@code super.method(...)}, the parent's method
 * declaration is invoked, which emits its own ENTER/EXIT pair. The resulting sequence on a
 * typical inheritance chain is last-in-first-out:
 *
 * <pre>{@code |> [ENTER] Child.m(...)
 * |> [ENTER] Parent.m(...)
 * |> [ENTER] Grandparent.m(...)
 * |< [EXIT] Grandparent.m(...)
 * |< [EXIT] Parent.m(...)
 * |< [EXIT] Child.m(...)}</pre>
 *
 * <p>If a particular layer's effective family resolves to {@link DoNotLog}, that layer
 * contributes neither ENTER nor EXIT, but the super-call chain still runs through it; remaining
 * layers continue to emit normally.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Repeatable(MethodLoggingProfiles.class)
public @interface MethodLoggingProfile {

    /**
     * Package name prefixes to match against the fully-qualified class name.
     *
     * <p>A class matches if its fully-qualified name equals a prefix exactly, or starts with {@code
     * prefix + "."}. A prefix that already ends with {@code "."} is matched as a plain starts-with
     * check. For example, {@code "com.example"} matches {@code com.example.Foo} and {@code
     * com.example.sub.Foo}, but not {@code com.exampleother.Foo}.
     *
     * <p>If omitted, empty, or all entries are blank, the profile matches no classes. Blank or
     * empty-string entries ({@code ""}, {@code " "}) are silently discarded during configuration
     * processing.
     *
     * @return package name prefixes to match
     */
    String[] includePackages() default {};

    /**
     * Package name prefixes to exclude after {@link #includePackages()} has matched.
     *
     * <p>A class is excluded if its fully-qualified name equals an exclude prefix exactly, or starts
     * with {@code excludePrefix + "."}. A prefix that already ends with {@code "."} is matched as a
     * plain starts-with check.
     *
     * <p>If omitted, empty, or all entries are blank, no package exclusion is applied. Blank or
     * empty-string entries ({@code ""}, {@code " "}) are silently discarded during configuration
     * processing.
     *
     * @return package name prefixes to exclude
     */
    String[] excludePackages() default {};

    /**
     * Simple class name suffixes to match, for example {@code {"Controller", "Service"}}.
     *
     * <p>A class matches if its simple name ends with at least one of the listed suffixes (OR
     * semantics within this list). This condition is combined with {@link #includePackages()} using
     * AND: both must match for the profile to apply.
     *
     * <p>If omitted, empty, or all entries are blank, the profile matches no classes. Blank or
     * empty-string entries ({@code ""}, {@code " "}) are silently discarded during configuration
     * processing.
     *
     * @return simple class name suffixes to match
     */
    String[] includeClassSuffixes() default {};

    /**
     * Names of fields to include in method logging for matching classes.
     *
     * <p>Each name must correspond to a {@link MethodLoggingField} declared on the same {@link
     * LogRegistry} class; referencing an unknown name is a configuration error.
     *
     * <p>An empty array is valid: the profile still matches classes, but no additional fields are
     * written to the log entry.
     *
     * <p><b>Appearance in ENTER lines:</b> extractor fields are emitted <em>after</em> the
     * method's own parameters. Each field renders as {@code name=value} separated by
     * {@code ", "}. Extractor fields do <b>not</b> appear on EXIT lines.
     *
     * @return the field names to include in method logging
     */
    String[] fields() default {};

    /**
     * Entry log level for classes matched by this profile.
     *
     * <p>The default is {@link LogLevel#INFO}. Use {@link LogLevel#OFF} to disable entry logging
     * while keeping exit logging unchanged.
     *
     * <p><b>Level semantics:</b> the configured level is the severity attached to the emitted
     * log line. The framework does not perform its own level filtering &mdash; whether a
     * configured DEBUG-level ENTER line actually reaches a sink depends on the underlying
     * logging system's level configuration. {@link LogLevel#OFF} is the one exception: it
     * disables emission entirely &mdash; no log line is emitted, regardless of backend
     * configuration.
     *
     * @return the entry log level
     */
    LogLevel entryLevel() default LogLevel.INFO;

    /**
     * Exit log level for classes matched by this profile.
     *
     * <p>The default is {@link LogLevel#INFO}. Use {@link LogLevel#OFF} to disable exit logging
     * while keeping entry logging unchanged.
     *
     * <p><b>Level semantics:</b> the configured level is the severity attached to the emitted
     * log line. The framework does not perform its own level filtering &mdash; whether a
     * configured DEBUG-level EXIT line actually reaches a sink depends on the underlying
     * logging system's level configuration. {@link LogLevel#OFF} is the one exception: it
     * disables emission entirely &mdash; no log line is emitted, regardless of backend
     * configuration.
     *
     * @return the exit log level
     */
    LogLevel exitLevel() default LogLevel.INFO;
}
