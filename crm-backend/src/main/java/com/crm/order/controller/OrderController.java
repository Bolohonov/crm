package com.crm.order.controller;

import com.crm.common.response.ApiResponse;
import com.crm.order.dto.OrderDto;
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
    public ResponseEntity<ApiResponse<OrderDto.PageResponse>> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID statusId,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var req = new OrderDto.FilterRequest();
        req.setCustomerId(customerId); req.setStatusId(statusId);
        req.setAuthorId(authorId); req.setPage(page); req.setSize(size);

        return ResponseEntity.ok(ApiResponse.ok(orderService.list(req)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<OrderDto.StatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getStats()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> create(
            @Valid @RequestBody OrderDto.CreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(orderService.create(request, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody OrderDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> changeStatus(
            @PathVariable UUID id,
            @RequestBody OrderDto.ChangeStatusRequest request,
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
