package org.libprunus.core.log.annotation;

/** Declares how a sensitive value should be masked. */
public enum MaskStrategy {
    /** Leaves the original value unchanged. */
    NONE,

    /** Replaces the original value with a fully masked representation. */
    ALL
}
