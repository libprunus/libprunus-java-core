package com.example.backend.catalog.repository;

import com.example.backend.catalog.error.CatalogErrorCode;
import com.example.backend.catalog.model.row.ProductRow;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.libprunus.core.error.ApiErrorException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final String COLUMNS = "id, sku, name, category, price_cents, quantity";

    private final JdbcClient jdbcClient;

    public JdbcProductRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ProductRow> findById(long id) {
        return jdbcClient
                .sql("select " + COLUMNS + " from product where id = :id")
                .param("id", id)
                .query(ProductRow.class)
                .optional();
    }

    @Override
    public List<ProductRow> findAll() {
        return jdbcClient.sql("select " + COLUMNS + " from product order by id").query(ProductRow.class).list().stream()
                .map(Objects::requireNonNull)
                .toList();
    }

    @Override
    public List<ProductRow> findByCategory(String category) {
        return jdbcClient
                .sql("select " + COLUMNS + " from product where category = :category order by id")
                .param("category", category)
                .query(ProductRow.class)
                .list()
                .stream()
                .map(Objects::requireNonNull)
                .toList();
    }

    @Override
    public boolean existsBySku(String sku) {
        Long count = jdbcClient
                .sql("select count(*) from product where sku = :sku")
                .param("sku", sku)
                .query(Long.class)
                .single();
        return count > 0;
    }

    @Override
    public ProductRow insert(String sku, String name, String category, long priceCents, int quantity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcClient
                    .sql("insert into product (sku, name, category, price_cents, quantity)"
                            + " values (:sku, :name, :category, :priceCents, :quantity)")
                    .param("sku", sku)
                    .param("name", name)
                    .param("category", category)
                    .param("priceCents", priceCents)
                    .param("quantity", quantity)
                    .update(keyHolder);
        } catch (DuplicateKeyException duplicateSku) {
            // unique(sku) is the source of truth; the service pre-check only shortcuts the
            // common case and cannot close the check-then-insert race.
            throw new ApiErrorException(CatalogErrorCode.DUPLICATE_SKU, "sku already exists", duplicateSku);
        }
        long id = Objects.requireNonNull(keyHolder.getKey(), "insert returned no generated key")
                .longValue();
        return new ProductRow(id, sku, name, category, priceCents, quantity);
    }
}
