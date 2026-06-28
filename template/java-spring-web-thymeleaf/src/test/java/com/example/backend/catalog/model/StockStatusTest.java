package com.example.backend.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockStatusTest {

    @ParameterizedTest
    @CsvSource({"0,OUT_OF_STOCK", "-1,OUT_OF_STOCK", "1,LOW_STOCK", "9,LOW_STOCK", "10,IN_STOCK", "100,IN_STOCK"})
    void fromQuantityClassifiesByThreshold(int quantity, StockStatus expected) {
        assertThat(StockStatus.fromQuantity(quantity)).isEqualTo(expected);
    }
}
