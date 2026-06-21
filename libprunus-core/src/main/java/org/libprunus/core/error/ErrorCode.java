package org.libprunus.core.error;

/**
 * Stable, transport-decoupled error identity. {@code code()} is the machine-readable identifier;
 * {@code category()} drives the per-outlet status mapping (e.g. HTTP status, gRPC status code).
 */
public interface ErrorCode {

    String code();

    ErrorCategory category();
}
