package com.example.backend.catalog.model;

public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    private static final int LOW_STOCK_THRESHOLD = 10;

    public static StockStatus fromQuantity(int quantity) {
        if (quantity <= 0) {
            return OUT_OF_STOCK;
        }
        if (quantity < LOW_STOCK_THRESHOLD) {
            return LOW_STOCK;
        }
        return IN_STOCK;
    }
}
