package com.example.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.backend.catalog.error.CatalogErrorCode;
import com.example.backend.catalog.model.row.ProductRow;
import com.example.backend.catalog.testutil.FakeProductRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.libprunus.core.error.ApiErrorException;

class ProductServiceTest {

    private FakeProductRepository repository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repository = new FakeProductRepository();
        service = new ProductService(repository);
    }

    @Test
    void createPersistsProductAndReturnsRowWithGeneratedId() {
        ProductRow row = service.create("SKU-1", "Widget", "tools", 1_999, 5);

        assertThat(row.id()).isPositive();
        assertThat(repository.findById(row.id())).contains(row);
    }

    @Test
    void createAcceptsValuesAtInclusiveBoundaries() {
        ProductRow row = service.create("x".repeat(64), "x".repeat(200), "x".repeat(100), 100_000_000L, 0);

        assertThat(row.id()).isPositive();
        assertThat(repository.findById(row.id())).contains(row);
    }

    @Test
    void createRejectsDuplicateSkuWithoutAddingSecondRow() {
        service.create("SKU-1", "Widget", "tools", 1_999, 5);

        Throwable thrown = catchThrowable(() -> service.create("SKU-1", "Other", "tools", 1_000, 1));

        assertThat(thrown).isInstanceOf(ApiErrorException.class);
        assertThat(((ApiErrorException) thrown).errorCode()).isEqualTo(CatalogErrorCode.DUPLICATE_SKU);
        assertThat(repository.findAll()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("invalidProducts")
    void createRejectsInvalidInputWithoutPersisting(
            String sku, String name, String category, long priceCents, int quantity) {
        Throwable thrown = catchThrowable(() -> service.create(sku, name, category, priceCents, quantity));

        assertThat(thrown).isInstanceOf(ApiErrorException.class);
        assertThat(((ApiErrorException) thrown).errorCode()).isEqualTo(CatalogErrorCode.INVALID_PRODUCT);
        assertThat(repository.findAll()).isEmpty();
    }

    private static Stream<Arguments> invalidProducts() {
        return Stream.of(
                Arguments.of(" ", "Widget", "tools", 1_999, 5),
                Arguments.of("x".repeat(65), "Widget", "tools", 1_999, 5),
                Arguments.of("SKU-1", " ", "tools", 1_999, 5),
                Arguments.of("SKU-1", "x".repeat(201), "tools", 1_999, 5),
                Arguments.of("SKU-1", "Widget", " ", 1_999, 5),
                Arguments.of("SKU-1", "Widget", "x".repeat(101), 1_999, 5),
                Arguments.of("SKU-1", "Widget", "tools", -1, 5),
                Arguments.of("SKU-1", "Widget", "tools", 100_000_001L, 5),
                Arguments.of("SKU-1", "Widget", "tools", 1_999, -1));
    }

    @Test
    void findByIdReturnsPersistedRow() {
        ProductRow created = service.create("SKU-1", "Widget", "tools", 1_999, 5);

        assertThat(service.findById(created.id())).isEqualTo(created);
    }

    @Test
    void findByIdThrowsNotFoundForUnknownId() {
        Throwable thrown = catchThrowable(() -> service.findById(404L));

        assertThat(thrown).isInstanceOf(ApiErrorException.class);
        assertThat(((ApiErrorException) thrown).errorCode()).isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void listWithBlankCategoryReturnsAllProducts() {
        service.create("SKU-1", "Widget", "tools", 1_999, 5);
        service.create("SKU-2", "Gadget", "gear", 2_999, 3);

        assertThat(service.list("")).hasSize(2);
    }

    @Test
    void listWithCategoryReturnsOnlyMatchingProducts() {
        service.create("SKU-1", "Widget", "tools", 1_999, 5);
        service.create("SKU-2", "Gadget", "gear", 2_999, 3);

        assertThat(service.list("gear"))
                .singleElement()
                .extracting(ProductRow::sku)
                .isEqualTo("SKU-2");
    }
}
