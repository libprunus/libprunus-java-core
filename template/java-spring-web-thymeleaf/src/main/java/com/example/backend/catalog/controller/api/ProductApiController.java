package com.example.backend.catalog.controller.api;

import com.example.backend.catalog.controller.api.dto.CreateProductRequest;
import com.example.backend.catalog.controller.api.dto.ProductResponse;
import com.example.backend.catalog.model.StockStatus;
import com.example.backend.catalog.model.row.ProductRow;
import com.example.backend.catalog.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService productService;

    public ProductApiController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(defaultValue = "") String category) {
        return productService.list(category).stream()
                .map(ProductApiController::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return toResponse(productService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        ProductRow created = productService.create(
                request.sku(), request.name(), request.category(), request.priceCents(), request.quantity());
        return toResponse(created);
    }

    private static ProductResponse toResponse(ProductRow row) {
        return new ProductResponse(
                row.id(),
                row.sku(),
                row.name(),
                row.category(),
                row.priceCents(),
                row.quantity(),
                StockStatus.fromQuantity(row.quantity()));
    }
}
