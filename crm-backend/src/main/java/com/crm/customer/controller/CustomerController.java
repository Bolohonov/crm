package com.crm.customer.controller;

import com.crm.common.response.ApiResponse;
import com.crm.customer.dto.CustomerDto;
import com.crm.customer.service.CustomerService;
import com.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API для работы с клиентами.
 *
 * GET    /customers              — список с поиском и пагинацией
 * GET    /customers/{id}        — карточка клиента
 * POST   /customers             — создать клиента
 * PUT    /customers/{id}        — обновить клиента
 * DELETE /customers/{id}        — удалить клиента
 * PATCH  /customers/{id}/status — изменить статус
 */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<ApiResponse<CustomerDto.PageResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var req = new CustomerDto.SearchRequest();
        req.setQuery(query);
        req.setType(type != null ? com.crm.customer.entity.CustomerType.valueOf(type) : null);
        req.setStatus(status);
        req.setPage(page);
        req.setSize(size);

        return ResponseEntity.ok(ApiResponse.ok(customerService.search(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDto.CustomerResponse>> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerDto.CustomerResponse>> create(
            @Valid @RequestBody CustomerDto.CreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(customerService.create(request, currentUser)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDto.CustomerResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        var req = new CustomerDto.UpdateRequest();
        req.setStatus(status);
        customerService.update(id, req);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
