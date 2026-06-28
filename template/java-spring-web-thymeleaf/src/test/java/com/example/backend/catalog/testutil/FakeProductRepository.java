package com.example.backend.catalog.testutil;

import com.example.backend.catalog.model.row.ProductRow;
import com.example.backend.catalog.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FakeProductRepository implements ProductRepository {

    private final List<ProductRow> rows = new ArrayList<>();
    private long sequence;

    @Override
    public Optional<ProductRow> findById(long id) {
        return rows.stream().filter(row -> row.id() == id).findFirst();
    }

    @Override
    public List<ProductRow> findAll() {
        return List.copyOf(rows);
    }

    @Override
    public List<ProductRow> findByCategory(String category) {
        return rows.stream().filter(row -> row.category().equals(category)).toList();
    }

    @Override
    public boolean existsBySku(String sku) {
        return rows.stream().anyMatch(row -> row.sku().equals(sku));
    }

    @Override
    public ProductRow insert(String sku, String name, String category, long priceCents, int quantity) {
        ProductRow row = new ProductRow(++sequence, sku, name, category, priceCents, quantity);
        rows.add(row);
        return row;
    }
}
