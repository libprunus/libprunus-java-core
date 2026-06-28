package com.example.backend.catalog.service;

import com.example.backend.catalog.error.CatalogErrorCode;
import com.example.backend.catalog.model.row.ProductRow;
import com.example.backend.catalog.repository.ProductRepository;
import java.util.List;
import org.libprunus.core.error.ApiErrorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final int MAX_SKU_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_CATEGORY_LENGTH = 100;
    private static final long MAX_PRICE_CENTS = 100_000_000L;

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProductRow create(String sku, String name, String category, long priceCents, int quantity) {
        validateSku(sku);
        validateName(name);
        validateCategory(category);
        validatePrice(priceCents);
        validateQuantity(quantity);
        return repository.insert(sku, name, category, priceCents, quantity);
    }

    public ProductRow findById(long id) {
        return repository
                .findById(id)
                .orElseThrow(
                        () -> new ApiErrorException(CatalogErrorCode.PRODUCT_NOT_FOUND, "product not found: " + id));
    }

    public List<ProductRow> list(String category) {
        if (category.isBlank()) {
            return repository.findAll();
        }
        return repository.findByCategory(category);
    }

    private void validateSku(String sku) {
        if (sku.isBlank()) {
            throw new ApiErrorException(CatalogErrorCode.INVALID_PRODUCT, "sku must not be blank");
        }
        if (sku.length() > MAX_SKU_LENGTH) {
            throw new ApiErrorException(
                    CatalogErrorCode.INVALID_PRODUCT, "sku must be at most " + MAX_SKU_LENGTH + " characters");
        }
        if (repository.existsBySku(sku)) {
            throw new ApiErrorException(CatalogErrorCode.DUPLICATE_SKU, "sku already exists");
        }
    }

    private void validateName(String name) {
        if (name.isBlank()) {
            throw new ApiErrorException(CatalogErrorCode.INVALID_PRODUCT, "name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ApiErrorException(
                    CatalogErrorCode.INVALID_PRODUCT, "name must be at most " + MAX_NAME_LENGTH + " characters");
        }
    }

    private void validateCategory(String category) {
        if (category.isBlank()) {
            throw new ApiErrorException(CatalogErrorCode.INVALID_PRODUCT, "category must not be blank");
        }
        if (category.length() > MAX_CATEGORY_LENGTH) {
            throw new ApiErrorException(
                    CatalogErrorCode.INVALID_PRODUCT,
                    "category must be at most " + MAX_CATEGORY_LENGTH + " characters");
        }
    }

    private void validatePrice(long priceCents) {
        if (priceCents < 0) {
            throw new ApiErrorException(CatalogErrorCode.INVALID_PRODUCT, "priceCents must not be negative");
        }
        if (priceCents > MAX_PRICE_CENTS) {
            throw new ApiErrorException(
                    CatalogErrorCode.INVALID_PRODUCT, "priceCents must not exceed " + MAX_PRICE_CENTS);
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new ApiErrorException(CatalogErrorCode.INVALID_PRODUCT, "quantity must not be negative");
        }
    }
}
