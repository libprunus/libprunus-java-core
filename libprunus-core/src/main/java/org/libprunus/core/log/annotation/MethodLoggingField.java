package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a named field extractor for method logging.
 *
 * <p>This annotation must be placed on a method in a {@link LogRegistry} class that satisfies all
 * of the following constraints; any violation is a configuration error:
 *
 * <ul>
 *   <li>The method must be {@code public}.
 *   <li>The method must be {@code static}.
 *   <li>The method must have no parameters.
 *   <li>The return type must not be {@code void}; any other type including primitives is allowed.
 *   <li>The {@link #value()} must be unique within the same {@link LogRegistry} class.
 * </ul>
 *
 * <p>The annotated method is invoked at logging time; its return value is written to the log entry
 * under the field name specified by {@link #value()}. Only fields referenced by a matching {@link
 * MethodLoggingProfile} are included in the log output.
 *
 * <h2>Failure isolation</h2>
 *
 * <p>If the annotated method throws when invoked at logging time, the exception is reported
 * through the framework's internal logging-failure path and is <em>not</em> propagated to the
 * caller. The business method's ENTER line is still emitted; the failed field is omitted from
 * the line. The framework guarantees that extractor failures never alter business code's
 * control flow.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MethodLoggingField {

    /**
     * Field name used in method logging records.
     *
     * <p>Must be unique within the same {@link LogRegistry} class. Duplicate names are a
     * configuration error.
     *
     * @return the field name
     */
    String value();
}
