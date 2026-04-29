package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares base package prefixes used for log processing routing (method logging and toString).
 *
 * <p>This annotation defines the global routing scope. A class outside this scope is not considered
 * for method logging or toString processing.
 *
 * <p>The value is an array to allow multiple independent package prefixes.
 *
 * <p>Prefix matching uses package-segment boundary rules: a prefix matches when the class name
 * equals the prefix or starts with {@code prefix + "."}. Therefore, {@code com.a} does not match
 * {@code com.ab.Foo}.
 *
 * <p>This global scope is a hard pre-filter. Profile-level package rules can only narrow this scope
 * and cannot expand it.
 *
 * <p>Blank or empty-string entries ({@code ""}, {@code " "}) are silently discarded during
 * configuration processing.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface LogBasePackages {
    /**
     * Base package prefixes used for log processing routing.
     *
     * <p>If all entries are blank (for example {@code {""}}), all entries are discarded and the
     * effective prefix set becomes empty, so no classes are matched.
     *
     * @return the base package prefixes
     */
    String[] value();
}
