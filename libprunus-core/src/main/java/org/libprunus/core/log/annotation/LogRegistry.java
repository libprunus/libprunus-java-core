package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the root class that carries system-wide logging conventions.
 *
 * <p>This annotation declares configuration identity only. Routing and rendering behavior are
 * defined by companion configuration annotations on the same class.
 *
 * <p>The registry role is explicit and non-inheritable.
 *
 * <p>In one processing run, exactly one selected registry class is parsed. Selection is an external
 * build input; the parser processes the selected class only and does not merge multiple registry
 * classes.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface LogRegistry {}
