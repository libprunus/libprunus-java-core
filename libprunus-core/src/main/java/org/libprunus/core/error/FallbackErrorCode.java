package org.libprunus.core.error;

/** The only error code libprunus ships: the generic fallback stamped on otherwise-unhandled exceptions. */
public enum FallbackErrorCode implements ErrorCode {
    INTERNAL(ErrorCategory.INTERNAL);

    private final ErrorCategory category;

    FallbackErrorCode(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
