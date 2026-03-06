package com.crm.dashboard.controller;

import com.crm.dashboard.dto.*;
import com.crm.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dashboard REST API
 *
 * Все эндпоинты требуют аутентификации.
 * Данные изолированы по tenant (TenantContext из JWT).
 *
 * GET /api/v1/dashboard/stats              — сводная статистика
 * GET /api/v1/dashboard/funnel             — воронка продаж
 * GET /api/v1/dashboard/revenue?months=6   — выручка по месяцам
 * GET /api/v1/dashboard/tasks/overdue      — просроченные задачи
 * GET /api/v1/dashboard/activity           — лента событий
 * GET /api/v1/dashboard/customers/top      — топ клиентов
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardStatsDto> getStats() {
        return ResponseEntity.ok(service.getStats());
    }

    @GetMapping("/funnel")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<List<FunnelStageDto>> getFunnel() {
        return ResponseEntity.ok(service.getFunnel());
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<List<RevenuePointDto>> getRevenue(
            @RequestParam(defaultValue = "6") int months) {
        months = Math.max(1, Math.min(24, months)); // clamp 1–24
        return ResponseEntity.ok(service.getRevenue(months));
    }

    @GetMapping("/tasks/overdue")
    @PreAuthorize("hasAuthority('TASK_VIEW')")
    public ResponseEntity<List<OverdueTaskDto>> getOverdueTasks(
            @RequestParam(defaultValue = "5") int limit) {
        limit = Math.max(1, Math.min(20, limit));
        return ResponseEntity.ok(service.getOverdueTasks(limit));
    }

    @GetMapping("/activity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RecentActivityDto>> getRecentActivity(
            @RequestParam(defaultValue = "8") int limit) {
        limit = Math.max(1, Math.min(50, limit));
        return ResponseEntity.ok(service.getRecentActivity(limit));
    }

    @GetMapping("/customers/top")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<List<TopCustomerDto>> getTopCustomers(
            @RequestParam(defaultValue = "5") int limit) {
        limit = Math.max(1, Math.min(20, limit));
        return ResponseEntity.ok(service.getTopCustomers(limit));
    }
}
