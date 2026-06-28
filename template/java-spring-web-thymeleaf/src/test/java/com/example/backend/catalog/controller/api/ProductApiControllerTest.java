package com.example.backend.catalog.controller.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.catalog.error.CatalogErrorCode;
import com.example.backend.catalog.model.row.ProductRow;
import com.example.backend.catalog.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.libprunus.core.error.ApiErrorException;
import org.libprunus.spring.error.ApiErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductApiController.class)
@Import(ApiErrorHandler.class)
class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void listReturnsProductsArrayWithDerivedStatus() throws Exception {
        when(productService.list("tools"))
                .thenReturn(List.of(new ProductRow(1L, "SKU-1", "Widget", "tools", 1_999, 5)));

        mockMvc.perform(get("/api/products").param("category", "tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$[0].status").value("LOW_STOCK"));
    }

    @Test
    void getReturnsJsonWithDerivedStockStatus() throws Exception {
        when(productService.findById(1L)).thenReturn(new ProductRow(1L, "SKU-1", "Widget", "tools", 1_999, 0));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));
    }

    @Test
    void getUnknownProductReturns404ProblemDetailWithErrorCode() throws Exception {
        when(productService.findById(404L))
                .thenThrow(new ApiErrorException(CatalogErrorCode.PRODUCT_NOT_FOUND, "product not found: 404"));

        mockMvc.perform(get("/api/products/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void createReturns201WithPersistedProduct() throws Exception {
        when(productService.create("SKU-1", "Widget", "tools", 1_999, 5))
                .thenReturn(new ProductRow(1L, "SKU-1", "Widget", "tools", 1_999, 5));

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"category\":\"tools\",\"priceCents\":1999,\"quantity\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("LOW_STOCK"));
    }

    @Test
    void createWithMissingRequiredFieldReturns400WithoutInvokingService() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Widget\",\"category\":\"tools\",\"priceCents\":1999,\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(productService);
    }
}
