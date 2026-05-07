package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a whitelist of value types allowed to invoke {@code toString()}.
 *
 * <p>Types in this whitelist are rendered using {@code toString()}.
 *
 * <p>Matching is hierarchy-aware at runtime: a value is considered whitelisted when its concrete
 * type, any superclass, or any implemented interface matches a configured type.
 *
 * <p>Duplicate configured types are deduplicated during configuration processing while preserving
 * declaration order.
 *
 * <p>Because matching is hierarchy-aware, configuring a shared supertype or interface is usually
 * sufficient to cover many concrete subtypes. Listing a large number of individual concrete classes
 * is rarely necessary; entries exceeding roughly 1000 may approach JVM method-body size limits in
 * the generated binding class.
 *
 * <p>For values outside the effective whitelist, rendering falls back to a safer structural path
 * (for example, class name plus identity), avoiding uncontrolled {@code toString()} execution.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface DirectToStringWhitelist {
    /**
     * The array of types allowed to be rendered directly via {@code toString()}.
     *
     * @return class types allowed for direct toString rendering
     */
    Class<?>[] value();
}
