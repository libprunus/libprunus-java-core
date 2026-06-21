package org.libprunus.core.error;

/** Transport-neutral error categories; each outlet (REST/gRPC) maps these to its own wire codes. */
public enum ErrorCategory {
    INVALID_ARGUMENT,
    UNAUTHENTICATED,
    PERMISSION_DENIED,
    NOT_FOUND,
    CONFLICT,
    FAILED_PRECONDITION,
    RESOURCE_EXHAUSTED,
    UNAVAILABLE,
    INTERNAL
}
