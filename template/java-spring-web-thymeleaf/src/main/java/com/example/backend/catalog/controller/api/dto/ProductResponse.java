package com.example.backend.catalog.controller.api.dto;

import com.example.backend.catalog.model.StockStatus;

public record ProductResponse(
        long id, String sku, String name, String category, long priceCents, int quantity, StockStatus status) {}
