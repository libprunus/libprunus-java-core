package com.example.backend.catalog.model.row;

public record ProductRow(long id, String sku, String name, String category, long priceCents, int quantity) {}
