package com.example.backend.catalog.controller.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @NotBlank String category,
        long priceCents,
        int quantity) {}
