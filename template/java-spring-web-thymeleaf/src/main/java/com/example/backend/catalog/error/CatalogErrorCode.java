package com.example.backend.catalog.error;

import org.libprunus.core.error.ErrorCategory;
import org.libprunus.core.error.ErrorCode;

public enum CatalogErrorCode implements ErrorCode {
    INVALID_PRODUCT(ErrorCategory.INVALID_ARGUMENT),
    DUPLICATE_SKU(ErrorCategory.CONFLICT),
    PRODUCT_NOT_FOUND(ErrorCategory.NOT_FOUND);

    private final ErrorCategory category;

    CatalogErrorCode(ErrorCategory category) {
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
