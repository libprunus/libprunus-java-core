package org.libprunus.core.error;

import org.jspecify.annotations.Nullable;

/**
 * Deliberately-signalled error carrying a stable {@link ErrorCode}. The message becomes the
 * client-facing {@code ProblemDetail.detail}, so it must be safe to expose &mdash; never put PII,
 * secrets, SQL, or internal exception text in it; internal diagnostics belong in the cause (logged,
 * never returned to the caller).
 */
public class ApiErrorException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiErrorException(ErrorCode errorCode, String safeMessage) {
        this(errorCode, safeMessage, null);
    }

    public ApiErrorException(ErrorCode errorCode, String safeMessage, @Nullable Throwable cause) {
        super(safeMessage, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
