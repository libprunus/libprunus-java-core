package org.libprunus.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a local skip switch for automated processing.
 *
 * <p>When declared on a class, only that class is excluded from automated processing.
 *
 * <p>When declared on a method, only that method declaration is excluded from automated
 * processing.
 *
 * <p>This annotation uses declared-only semantics: processing checks only annotations declared on
 * the current class or current method. The annotation is not inherited across parent classes,
 * child classes, implemented interfaces, or overridden methods.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AutomatedProcessingIgnore {}
