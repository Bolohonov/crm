package com.crm.export;

import com.crm.rbac.config.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Контроллер экспорта данных.
 *
 * Эндпоинты:
 *   GET /admin/export/customers.xlsx  — клиенты в Excel
 *   GET /admin/export/customers.csv   — клиенты в CSV
 *   GET /admin/export/orders.xlsx     — заказы в Excel
 *   GET /admin/export/orders.csv      — заказы в CSV
 *
 * Параметры (опциональные):
 *   managerId   — UUID менеджера (фильтр по автору/ответственному)
 *   statusCode  — код статуса заказа (только для заказов)
 *
 * Доступ: ADMIN или пользователи с правом EXPORT_DATA.
 */
@Slf4j
@RestController
@RequestMapping("/admin/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "Экспорт данных в Excel и CSV")
public class ExportController {

    private final ExportService exportService;

    // ── Клиенты ───────────────────────────────────────────────────────

    @GetMapping(value = "/customers.xlsx",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasRole('ADMIN') or @sec.has('EXPORT_DATA')")
    @Operation(summary = "Экспорт клиентов в Excel")
    public ResponseEntity<byte[]> exportCustomersExcel(
        @Parameter(description = "UUID менеджера (фильтр)")
        @RequestParam(required = false) UUID managerId
    ) throws IOException {
        log.info("Export customers.xlsx managerId={}", managerId);
        byte[] data = exportService.exportCustomersExcel(managerId);
        return fileResponse(data,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "customers-" + today() + ".xlsx");
    }

    @GetMapping(value = "/customers.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasRole('ADMIN') or @sec.has('EXPORT_DATA')")
    @Operation(summary = "Экспорт клиентов в CSV")
    public ResponseEntity<byte[]> exportCustomersCsv(
        @RequestParam(required = false) UUID managerId
    ) {
        log.info("Export customers.csv managerId={}", managerId);
        byte[] data = exportService.exportCustomersCsv(managerId);
        return fileResponse(data, "text/csv;charset=UTF-8",
            "customers-" + today() + ".csv");
    }

    // ── Заказы ────────────────────────────────────────────────────────

    @GetMapping(value = "/orders.xlsx",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasRole('ADMIN') or @sec.has('EXPORT_DATA')")
    @Operation(summary = "Экспорт заказов в Excel")
    public ResponseEntity<byte[]> exportOrdersExcel(
        @Parameter(description = "UUID менеджера (фильтр по автору заказа)")
        @RequestParam(required = false) UUID managerId,
        @Parameter(description = "Код статуса: NEW, PICKING, SHIPPED, DELIVERED, ARCHIVED")
        @RequestParam(required = false) String statusCode
    ) throws IOException {
        log.info("Export orders.xlsx managerId={} statusCode={}", managerId, statusCode);
        byte[] data = exportService.exportOrdersExcel(managerId, statusCode);
        return fileResponse(data,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "orders-" + today() + ".xlsx");
    }

    @GetMapping(value = "/orders.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasRole('ADMIN') or @sec.has('EXPORT_DATA')")
    @Operation(summary = "Экспорт заказов в CSV")
    public ResponseEntity<byte[]> exportOrdersCsv(
        @RequestParam(required = false) UUID managerId,
        @RequestParam(required = false) String statusCode
    ) {
        log.info("Export orders.csv managerId={} statusCode={}", managerId, statusCode);
        byte[] data = exportService.exportOrdersCsv(managerId, statusCode);
        return fileResponse(data, "text/csv;charset=UTF-8",
            "orders-" + today() + ".csv");
    }

    // ── Утилиты ───────────────────────────────────────────────────────

    private ResponseEntity<byte[]> fileResponse(byte[] data, String contentType, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
            .replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length))
            .header("X-Content-Type-Options", "nosniff")
            .body(data);
    }

    private String today() {
        return LocalDate.now().toString(); // 2026-03-01
    }
}
