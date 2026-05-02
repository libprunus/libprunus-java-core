package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits recursion depth used for nested object rendering.
 *
 * <p>Depth baseline and increment rules:
 *
 * <ul>
 *   <li>top-level argument depth starts at {@code 0}
 *   <li>object property value depth = parent depth + {@code 1}
 *   <li>collection/array element depth = container depth + {@code 1}
 *   <li>map key depth and map value depth = map depth + {@code 1}
 * </ul>
 *
 * <p>When a value's depth has reached the limit, the value itself is rendered as {@code
 * [DEPTH_EXCEEDED]} and is not expanded further. For a container or object at the depth limit, no
 * elements or fields are rendered individually; the entire node is replaced by the placeholder.
 *
 * <p>Value semantics:
 *
 * <ul>
 *   <li>negative: invalid, compilation will fail
 *   <li>{@code 0}: top-level values are depth-limited
 *   <li>positive: maximum allowed depth before replacement
 *   <li>hard global upper bound: {@code 64}. Values above this will fail compilation
 * </ul>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface MaxObjectDepth {

    /**
     * The maximum recursion depth.
     *
     * @return the maximum depth value
     */
    int value();
}
