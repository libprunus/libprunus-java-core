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
 * <p>A profile is declared on a {@link LogRegistry} class and is evaluated only for classes that
 * already fall within the global method-logging scope defined by {@link LogBasePackages}. A
 * matching profile determines both the additional logging fields and the entry/exit log levels for
 * that class.
 *
 * <p>Profile declaration order has no semantic meaning. If multiple {@link MethodLoggingProfile}
 * declarations can match the same class, configuration processing fails at compile time.
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
     * <p>These prefixes further narrow the classes already admitted by {@link LogBasePackages}; they
     * do not replace, inherit, or bypass the global package scope.
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
     * Names of fields to include in method logging for matching classes, in declaration order.
     *
     * <p>Each name must correspond to a {@link MethodLoggingField} declared on the same {@link
     * LogRegistry} class; referencing an unknown name causes configuration processing to fail.
     *
     * <p>An empty array is valid: the profile still matches classes, but no additional fields are
     * written to the log entry.
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
     * @return the entry log level
     */
    LogLevel entryLevel() default LogLevel.INFO;

    /**
     * Exit log level for classes matched by this profile.
     *
     * <p>The default is {@link LogLevel#INFO}. Use {@link LogLevel#OFF} to disable exit logging while
     * keeping entry logging unchanged.
     *
     * @return the exit log level
     */
    LogLevel exitLevel() default LogLevel.INFO;
}
