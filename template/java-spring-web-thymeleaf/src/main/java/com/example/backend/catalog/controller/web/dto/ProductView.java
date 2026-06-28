package com.example.backend.catalog.controller.web.dto;

import com.example.backend.catalog.model.StockStatus;

public record ProductView(long id, String sku, String name, String category, String priceDisplay, StockStatus status) {}
