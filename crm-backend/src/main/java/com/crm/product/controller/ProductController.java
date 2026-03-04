package com.crm.product.controller;

import com.crm.common.response.ApiResponse;
import com.crm.product.dto.ProductDto;
import com.crm.product.service.ProductService;
import com.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GET    /products            — список товаров
 * GET    /products/{id}       — товар по ID
 * POST   /products            — создать товар
 * PUT    /products/{id}       — обновить товар
 * PATCH  /products/{id}/active — включить/выключить
 * DELETE /products/{id}       — удалить товар
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProductDto.PageResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "true") boolean onlyActive,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var req = new ProductDto.SearchRequest();
        req.setQuery(query); req.setCategoryId(categoryId);
        req.setOnlyActive(onlyActive); req.setPage(page); req.setSize(size);

        return ResponseEntity.ok(ApiResponse.ok(productService.search(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> create(
            @Valid @RequestBody ProductDto.CreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(productService.create(request, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto.ProductResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, request)));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<Void>> setActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        productService.setActive(id, active);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
