package com.example.backend.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.backend.catalog.error.CatalogErrorCode;
import com.example.backend.catalog.model.row.ProductRow;
import org.junit.jupiter.api.Test;
import org.libprunus.core.error.ApiErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@Import(JdbcProductRepository.class)
@Sql(scripts = "classpath:schema.sql")
class JdbcProductRepositoryIntegrationTest {

    @Autowired
    private JdbcProductRepository repository;

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findAllReturnsInsertedRowsOrderedById() {
        ProductRow first = repository.insert("SKU-1", "Widget", "tools", 1_999, 5);
        ProductRow second = repository.insert("SKU-2", "Gadget", "gear", 2_999, 3);

        assertThat(repository.findAll()).containsExactly(first, second);
    }

    @Test
    void findByCategoryReturnsOnlyMatchingRows() {
        repository.insert("SKU-1", "Widget", "tools", 1_999, 5);
        ProductRow gear = repository.insert("SKU-2", "Gadget", "gear", 2_999, 3);

        assertThat(repository.findByCategory("gear")).containsExactly(gear);
    }

    @Test
    void existsBySkuReflectsInsertedRows() {
        repository.insert("SKU-1", "Widget", "tools", 1_999, 5);

        assertThat(repository.existsBySku("SKU-1")).isTrue();
        assertThat(repository.existsBySku("SKU-X")).isFalse();
    }

    @Test
    void insertAssignsGeneratedIdAndFindByIdReturnsRow() {
        ProductRow inserted = repository.insert("SKU-1", "Widget", "tools", 1_999, 5);

        assertThat(inserted.id()).isPositive();
        assertThat(repository.findById(inserted.id())).contains(inserted);
    }

    @Test
    void insertRejectsDuplicateSkuAndKeepsFirstRow() {
        ProductRow first = repository.insert("SKU-1", "Widget", "tools", 1_999, 5);

        Throwable thrown = catchThrowable(() -> repository.insert("SKU-1", "Other", "gear", 1_000, 1));

        assertThat(thrown).isInstanceOf(ApiErrorException.class);
        assertThat(((ApiErrorException) thrown).errorCode()).isEqualTo(CatalogErrorCode.DUPLICATE_SKU);
        assertThat(repository.findAll()).containsExactly(first);
    }
}
