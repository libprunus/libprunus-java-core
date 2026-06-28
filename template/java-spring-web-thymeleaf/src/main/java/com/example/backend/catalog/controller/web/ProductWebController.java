package com.example.backend.catalog.controller.web;

import com.example.backend.catalog.controller.web.dto.ProductView;
import com.example.backend.catalog.model.StockStatus;
import com.example.backend.catalog.model.row.ProductRow;
import com.example.backend.catalog.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/products")
public class ProductWebController {

    private final ProductService productService;

    public ProductWebController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String category, Model model) {
        model.addAttribute(
                "products",
                productService.list(category).stream()
                        .map(ProductWebController::toView)
                        .toList());
        return "products";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        model.addAttribute("product", toView(productService.findById(id)));
        return "product";
    }

    private static ProductView toView(ProductRow row) {
        String priceDisplay = "%d.%02d".formatted(row.priceCents() / 100, row.priceCents() % 100);
        return new ProductView(
                row.id(),
                row.sku(),
                row.name(),
                row.category(),
                priceDisplay,
                StockStatus.fromQuantity(row.quantity()));
    }
}
