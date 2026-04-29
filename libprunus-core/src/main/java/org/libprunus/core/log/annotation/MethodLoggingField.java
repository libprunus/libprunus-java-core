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
 * of the following constraints; any violation causes configuration processing to fail:
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
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MethodLoggingField {

    /**
     * Field name used in method logging records.
     *
     * <p>Must be unique within the same {@link LogRegistry} class. Duplicate names cause
     * configuration processing to fail.
     *
     * @return the field name
     */
    String value();
}
