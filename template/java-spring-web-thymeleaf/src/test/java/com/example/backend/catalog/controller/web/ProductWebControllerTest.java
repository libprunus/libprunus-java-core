package com.example.backend.catalog.controller.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.backend.catalog.controller.web.dto.ProductView;
import com.example.backend.catalog.error.CatalogErrorCode;
import com.example.backend.catalog.model.StockStatus;
import com.example.backend.catalog.model.row.ProductRow;
import com.example.backend.catalog.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.libprunus.core.error.ApiErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductWebController.class)
class ProductWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void listRendersProductsTemplateWithViewModels() throws Exception {
        when(productService.list("")).thenReturn(List.of(new ProductRow(1L, "SKU-1", "Widget", "tools", 1_999, 5)));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(model().attributeExists("products"));
    }

    @Test
    void detailRendersProductTemplateWithFormattedPriceAndStatus() throws Exception {
        when(productService.findById(1L)).thenReturn(new ProductRow(1L, "SKU-1", "Widget", "tools", 1_999, 0));

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("product"))
                .andExpect(model().attribute(
                                "product",
                                new ProductView(1L, "SKU-1", "Widget", "tools", "19.99", StockStatus.OUT_OF_STOCK)));
    }

    @Test
    void detailWithUnknownIdRendersErrorViewWith404() throws Exception {
        when(productService.findById(404L))
                .thenThrow(new ApiErrorException(CatalogErrorCode.PRODUCT_NOT_FOUND, "product not found: 404"));

        mockMvc.perform(get("/products/404"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("message", "product not found: 404"))
                .andExpect(model().attributeDoesNotExist("product"));
    }
}
