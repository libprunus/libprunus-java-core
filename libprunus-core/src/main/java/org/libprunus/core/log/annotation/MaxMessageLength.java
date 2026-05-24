package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits the maximum rendered length of a complete log message produced by a logging call site.
 *
 * <p>{@code @MaxMessageLength} imposes a single unified budget on the total output accumulated across
 * all arguments in one log statement.
 *
 * <p>The total rendered length is a strict upper bound: at every externally observable point the
 * message length will not exceed {@code value()}. To honor that bound under pressure, the runtime
 * may sacrifice marker presence, marker content, and the visual integrity of any trailing escape
 * sequence (for example, a {@code \\uXXXX} emitted from a {@code char[]} or single-{@code char}
 * render path may be cut mid-sequence at the truncation point). Do not base business processing on
 * final length, marker presence, or marker content.
 *
 * <p>Value semantics:
 *
 * <ul>
 *   <li>negative: invalid
 *   <li>{@code 0} to {@code 15}: normalized to {@code 16}
 *   <li>{@code >= 16}: strict upper bound on total rendered message length
 *   <li>hard global upper bound: {@code 1048576} (1 MB); values above this are invalid
 * </ul>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface MaxMessageLength {

    int MIN_VALUE = 16;

    int MAX_VALUE = 1_048_576;

    int DEFAULT_VALUE = 512;

    /**
     * The maximum total log message length.
     *
     * @return the maximum length value
     */
    int value();
}
