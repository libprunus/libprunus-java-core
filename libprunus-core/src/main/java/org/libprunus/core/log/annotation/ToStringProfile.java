package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Routing rule that maps classes to controlled toString rewriting behavior.
 *
 * <p>A profile is declared on a {@link LogRegistry} class and is evaluated only for classes that
 * already fall within the global logging scope defined by {@link LogBasePackages}.
 *
 * <p>Profile declaration order has no semantic meaning. If multiple {@link ToStringProfile}
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
 * <p>If no profile matches a class, no toString rewrite is applied for that class.
 *
 * <p>The container annotation {@link ToStringProfiles} is generated automatically by the compiler
 * when multiple profiles are declared.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Repeatable(ToStringProfiles.class)
public @interface ToStringProfile {

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
     * Simple class name suffixes to match, for example {@code {"Dto", "Response"}}.
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
     * Class-name format used when this profile matches.
     *
     * <p>Default is {@link LogClassNameFormat#SIMPLE}.
     *
     * @return class-name rendering format
     */
    LogClassNameFormat classNameFormat() default LogClassNameFormat.SIMPLE;
}
