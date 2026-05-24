package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a target as explicitly visible in the log output: rendered values pass through to the
 * framework's standard rendering pipeline verbatim.
 *
 * <p>{@code @DoLog} is one of three mutually exclusive family annotations. See {@link Sensitive}
 * for the canonical resolution algorithm shared by all three families (declaration-class chain,
 * closeness rule, layer-by-layer traversal, same-layer multi-family conflict, mutual exclusion,
 * parameter-target restrictions).
 *
 * <h2>{@code @DoLog}-specific effect</h2>
 *
 * <p>When the effective family resolution lands on {@code @DoLog}, the target's value is
 * rendered as the framework would render it without any family annotation in play.
 *
 * <p>The typical use case is to opt back into plain rendering at a closer scope than an inherited
 * or enclosing {@link Sensitive} or {@link DoNotLog} policy &mdash; for example, marking a single
 * field {@code @DoLog} on an otherwise {@code @Sensitive} class so that one field renders
 * verbatim while peers remain masked.
 *
 * <p>Note: {@code @DoLog} is exactly as authoritative as {@link Sensitive} and {@link DoNotLog}
 * within the resolution algorithm. It is not a weaker "default" &mdash; the moment the traversal
 * reaches a layer carrying {@code @DoLog}, resolution terminates and the target renders verbatim.
 *
 * <p><b>null values:</b> {@code null} renders as the literal string {@code "null"}.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface DoLog {}
