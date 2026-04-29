package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits the maximum rendered length of a single object value.
 *
 * <p>This limit is best effort. Final output length may still change because of truncation marker
 * handling and rendering-path differences. Do not base business processing on final length or
 * marker content.
 *
 * <p>Value semantics:
 *
 * <ul>
 *   <li>negative: invalid, compilation will fail
 *   <li>{@code 0} to {@code 15}: accepted and normalized to {@code 16} at compile time
 *   <li>{@code >= 16}: best-effort upper bound on object string representation length
 *   <li>hard global upper bound: {@code 1048576} (1 MB). Values above this will fail
 *       compilation
 * </ul>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface MaxObjectLength {

    /**
     * The maximum object length.
     *
     * @return the maximum length value
     */
    int value();
}
