package com.example.backend.catalog.repository;

import com.example.backend.catalog.model.row.ProductRow;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<ProductRow> findById(long id);

    List<ProductRow> findAll();

    List<ProductRow> findByCategory(String category);

    boolean existsBySku(String sku);

    ProductRow insert(String sku, String name, String category, long priceCents, int quantity);
}
