package com.crm.order.controller;
import com.crm.order.dto.OrderStatsResponse;
import com.crm.order.dto.OrderPageResponse;
import com.crm.order.dto.OrderChangeStatusRequest;
import com.crm.order.dto.OrderFilterRequest;
import com.crm.order.dto.OrderUpdateRequest;
import com.crm.order.dto.OrderCreateRequest;

import com.crm.order.dto.OrderResponse;

import com.crm.common.response.ApiResponse;
import com.crm.order.service.OrderService;
import com.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GET    /orders              — список заказов
 * GET    /orders/stats        — статистика по статусам
 * GET    /orders/{id}         — заказ по ID
 * POST   /orders              — создать заказ
 * PUT    /orders/{id}         — обновить заказ (позиции + статус)
 * PATCH  /orders/{id}/status  — изменить статус
 * DELETE /orders/{id}         — удалить заказ
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<OrderPageResponse>> list(OrderFilterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.list(req)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<OrderStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getStats()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(orderService.create(request, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> changeStatus(
            @PathVariable UUID id,
            @RequestBody OrderChangeStatusRequest request,
            @AuthenticationPrincipal User currentUser) {
        orderService.changeStatus(id, request.getStatusId(), currentUser, request.getComment());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** Допустимые переходы для UI (подсветка кнопок) */
    @GetMapping("/{id}/allowed-transitions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<java.util.Set<String>>> allowedTransitions(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        boolean isAdmin = "ADMIN".equals(
            currentUser.getUserType() != null ? currentUser.getUserType().name() : "");
        return ResponseEntity.ok(ApiResponse.ok(
            orderService.allowedTransitions(id, isAdmin)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        orderService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
