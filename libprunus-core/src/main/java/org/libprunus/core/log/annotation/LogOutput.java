package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares logging output policy for classes, methods, parameters, and fields.
 *
 * <p>Method logging scenario:
 *
 * <p>When declared on a type, this annotation defines the default output policy for all public
 * instance method parameters and return values of that type.
 *
 * <p>When declared on a method, this annotation applies to that method's parameters and return
 * value.
 *
 * <p>When declared on a parameter, this annotation applies to that parameter and overrides
 * higher-level declarations.
 *
 * <p>toString scenario:
 *
 * <p>When declared on a type, this annotation defines the default output policy for all fields of
 * that type.
 *
 * <p>When declared on a field, this annotation applies to that field and overrides type-level
 * declaration.
 *
 * <p>Resolution follows nearest-declaration-first semantics along the inheritance chain at each
 * declaration position.
 *
 * <p>For multiple ancestors at the same priority and same hierarchy depth, resolution uses a
 * layer-first rule:
 *
 * <ul>
 *   <li>If any declaration has {@link #ignore()} as {@code true}, that layer resolves to ignore.
 *   <li>Otherwise, if any declaration has {@link #strategy()} as {@link MaskStrategy#ALL}, that
 *       layer resolves to {@link MaskStrategy#ALL}.
 *   <li>Otherwise, that layer contributes no decisive hit and resolution continues to outer layers.
 *   <li>If this non-decisive layer contains conflicting non-ALL strategies, the conflict is
 *       deferred and still allows outer-layer lookup.
 * </ul>
 *
 * <p>When no {@code @LogOutput} is present anywhere in the resolved chain for a given target, the
 * effective policy is {@link #ignore()} {@code false} and {@link #strategy()} {@link
 * MaskStrategy#NONE}.
 *
 * <p>If the resolved chain ends without any decisive hit (ignore or ALL) and a deferred conflict
 * remains unresolved, configuration processing should fail at build time.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface LogOutput {

    /**
     * Whether logging output is fully suppressed for the target.
     *
     * <p>When {@code true}, no value is written for the target and {@link #strategy()} is ignored.
     *
     * @return {@code true} to suppress output, {@code false} to keep output enabled
     */
    boolean ignore() default false;

    /**
     * Masking strategy used when output is enabled.
     *
     * <p>{@link MaskStrategy#ALL} means the value is fully masked.
     *
     * @return masking strategy for rendered output
     */
    MaskStrategy strategy() default MaskStrategy.ALL;
}
