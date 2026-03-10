package com.crm.order.service;
import com.crm.order.dto.OrderStatsResponse;
import com.crm.order.dto.OrderPageResponse;
import com.crm.order.dto.OrderFilterRequest;
import com.crm.order.dto.OrderUpdateRequest;
import com.crm.order.dto.OrderCreateRequest;

import com.crm.order.dto.ItemRequest;
import com.crm.order.dto.ItemResponse;
import com.crm.order.dto.OrderResponse;

import com.crm.common.exception.AppException;
import com.crm.kafka.producer.OrderStatusChangedProducer;
import com.crm.order.entity.Order;
import com.crm.order.entity.OrderItem;
import com.crm.order.repository.OrderItemRepository;
import com.crm.order.repository.OrderRepository;
import com.crm.product.repository.ProductRepository;
import com.crm.rbac.config.Permissions;
import com.crm.sse.SseNotificationService;
import com.crm.sse.SseOrderEvent;
import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.crm.audit.service.AuditService;
import com.crm.status.service.StatusTransitionService;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository                orderRepository;
    private final OrderItemRepository            itemRepository;
    private final ProductRepository              productRepository;
    private final JdbcTemplate                   jdbc;
    private final AuditService                   auditService;
    private final StatusTransitionService        transitionService;
    private final OrderStatusChangedProducer     kafkaProducer;
    private final SseNotificationService         sseService;

    // ── Список ───────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.ORDER_VIEW + "')")
    public OrderPageResponse list(OrderFilterRequest req) {
        int size   = Math.min(req.getSize(), 100);
        int offset = req.getPage() * size;

        var orders = orderRepository.findAll(
            uuid(req.getCustomerId()), uuid(req.getStatusId()), uuid(req.getAuthorId()),
            size, offset
        );
        long total = orderRepository.countAll(
            uuid(req.getCustomerId()), uuid(req.getStatusId()), uuid(req.getAuthorId())
        );

        return OrderPageResponse.builder()
            .content(orders.stream().map(o -> toResponse(o, true)).toList())
            .totalElements(total)
            .totalPages((int) Math.ceil((double) total / size))
            .page(req.getPage()).size(size)
            .build();
    }

    // ── Детальный заказ ───────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.ORDER_VIEW + "')")
    public OrderResponse getById(UUID id) {
        return toResponse(find(id), true);
    }

    // ── Статистика ────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.ORDER_VIEW + "')")
    public OrderStatsResponse getStats() {
        var rows = orderRepository.getStatsByStatus();
        long total = 0, newCount = 0, doneCount = 0;
        BigDecimal revenue = BigDecimal.ZERO;

        for (var row : rows) {
            long cnt = ((Number) row.get("cnt")).longValue();
            BigDecimal sum = (BigDecimal) row.get("total");
            total += cnt;
            revenue = revenue.add(sum);
            String code = (String) row.get("status_code");
            if ("NEW".equals(code))  newCount  = cnt;
            if ("DONE".equals(code)) doneCount = cnt;
        }

        return OrderStatsResponse.builder()
            .totalOrders(total).totalRevenue(revenue)
            .newOrders(newCount).completedOrders(doneCount)
            .build();
    }

    // ── Создание ──────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.ORDER_CREATE + "')")
    @Transactional
    public OrderResponse create(OrderCreateRequest req, User author) {
        Order order = Order.builder()
            .customerId(req.getCustomerId())
            .authorId(author.getId())
            .statusId(req.getStatusId())
            .comment(req.getComment())
            .totalAmount(BigDecimal.ZERO)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        order = orderRepository.save(order);

        // Сохраняем позиции
        List<OrderItem> items = buildItems(order.getId(), req.getItems());
        items.forEach(itemRepository::save);

        // Пересчитываем и сохраняем итог
        BigDecimal total = recalcTotal(order.getId());
        orderRepository.updateTotal(order.getId(), total);
        order.setTotalAmount(total);

        log.info("Order created: {} customer={} total={}", order.getId(), order.getCustomerId(), total);
        return toResponse(order, true);
    }

    // ── Обновление ────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.ORDER_EDIT + "')")
    @Transactional
    public OrderResponse update(UUID id, OrderUpdateRequest req) {
        Order order = find(id);

        if (req.getStatusId() != null) order.setStatusId(req.getStatusId());
        if (req.getComment()  != null) order.setComment(req.getComment());
        order.setUpdatedAt(Instant.now());

        // Если пришли новые позиции — полная замена
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            itemRepository.deleteByOrderId(id);
            List<OrderItem> items = buildItems(id, req.getItems());
            items.forEach(itemRepository::save);
            BigDecimal total = recalcTotal(id);
            order.setTotalAmount(total);
        }

        order = orderRepository.save(order);
        return toResponse(order, true);
    }

    // ── Смена статуса (с валидацией перехода и аудитом) ──────────────

    @Transactional
    @PreAuthorize("@sec.has('" + Permissions.ORDER_EDIT + "')")
    public void changeStatus(UUID id, UUID newStatusId, User actor, String comment) {
        Order order = find(id);

        Map<String, Object> fromStatus = queryStatusById(order.getStatusId());
        Map<String, Object> toStatus   = queryStatusById(newStatusId);
        String fromCode = (String) fromStatus.get("code");
        String toCode   = (String) toStatus.get("code");

        boolean isAdmin = actor.getUserType() != null &&
            "ADMIN".equals(actor.getUserType().name());
        transitionService.validate("orders", fromCode, toCode, isAdmin);

        orderRepository.updateStatus(id, newStatusId);

        String actorName = actor.getLastName() + " " + actor.getFirstName().charAt(0) + ".";
        auditService.logStatusChange("ORDER", id, fromCode, toCode,
            actor.getId(), actorName, comment);

        // ── Kafka: уведомляем магазин об изменении статуса ────────────
        kafkaProducer.enqueue(
            id,
            order.getExternalOrderId(),
            order.getShopOrderUuid(),
            fromCode,
            toCode,
            actorName,
            comment
        );

        // ── SSE: push всем вкладкам текущего тенанта ──────────────────
        sseService.broadcast(
            TenantContext.get(),
            "order.status_changed",
            SseOrderEvent.statusChanged(id, order.getExternalOrderId(), fromCode, toCode)
        );
    }

    /** Обратная совместимость — без явного Actor (системные вызовы) */
    @Transactional
    @PreAuthorize("@sec.has('" + Permissions.ORDER_EDIT + "')")
    public void changeStatus(UUID id, UUID newStatusId) {
        Order order = find(id);
        Map<String, Object> fromStatus = queryStatusById(order.getStatusId());
        Map<String, Object> toStatus   = queryStatusById(newStatusId);
        String fromCode = (String) fromStatus.get("code");
        String toCode   = (String) toStatus.get("code");
        transitionService.validate("orders", fromCode, toCode, false);
        orderRepository.updateStatus(id, newStatusId);
        auditService.logStatusChange("ORDER", id, fromCode, toCode, null, "system", null);

        // ── Kafka: системная смена статуса
        kafkaProducer.enqueue(
            id,
            order.getExternalOrderId(),
            order.getShopOrderUuid(),
            fromCode,
            toCode,
            "system",
            null
        );

        // ── SSE: push всем вкладкам текущего тенанта ──────────────────
        sseService.broadcast(
            TenantContext.get(),
            "order.status_changed",
            SseOrderEvent.statusChanged(id, order.getExternalOrderId(), fromCode, toCode)
        );
    }

    /** Допустимые переходы для UI */
    @PreAuthorize("@sec.has('" + Permissions.ORDER_VIEW + "')")
    public java.util.Set<String> allowedTransitions(UUID id, boolean isAdmin) {
        Order order = find(id);
        Map<String, Object> currentStatus = queryStatusById(order.getStatusId());
        return transitionService.allowedTargets("orders", (String) currentStatus.get("code"), isAdmin);
    }

    // ── Удаление ──────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.ORDER_CREATE + "')")
    @Transactional
    public void delete(UUID id) {
        if (!orderRepository.existsById(id)) throw AppException.notFound("Заказ");
        itemRepository.deleteByOrderId(id);
        orderRepository.deleteById(id);
    }

    // ── Маппинг ───────────────────────────────────────────────────────

    private OrderResponse toResponse(Order o, boolean withItems) {
        var statusRow = queryStatusById(o.getStatusId());
        String customerName = queryCustomerName(o.getCustomerId());
        String authorName   = getUserName(o.getAuthorId());

        var builder = OrderResponse.builder()
            .id(o.getId())
            .customerId(o.getCustomerId()).customerName(customerName)
            .authorId(o.getAuthorId()).authorName(authorName)
            .statusId(o.getStatusId())
            .statusName((String)  statusRow.getOrDefault("name",  ""))
            .statusCode((String)  statusRow.getOrDefault("code",  ""))
            .statusColor((String) statusRow.getOrDefault("color", "#888"))
            .comment(o.getComment())
            .totalAmount(o.getTotalAmount())
            .externalOrderId(o.getExternalOrderId())
            .shopOrderUuid(o.getShopOrderUuid())
            .fromShop(o.getShopOrderUuid() != null)
            .createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt());

        if (withItems) {
            var items = itemRepository.findByOrderId(o.getId())
                .stream().map(this::toItemResponse).toList();
            builder.items(items);
        }

        return builder.build();
    }

    private ItemResponse toItemResponse(OrderItem item) {
        var productOpt = productRepository.findById(item.getProductId());
        String name = productOpt.map(p -> p.getName()).orElse("—");
        String sku  = productOpt.map(p -> p.getSku()).orElse(null);
        String unit = productOpt.map(p -> p.getUnit()).orElse(null);

        return ItemResponse.builder()
            .id(item.getId()).productId(item.getProductId())
            .productName(name).productSku(sku).productUnit(unit)
            .quantity(item.getQuantity()).price(item.getPrice())
            .totalPrice(item.getTotalPrice())
            .build();
    }

    // ── Приватные хелперы ─────────────────────────────────────────────

    private Order find(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> AppException.notFound("Заказ"));
    }

    private List<OrderItem> buildItems(UUID orderId, List<ItemRequest> reqs) {
        List<OrderItem> result = new ArrayList<>();
        for (var req : reqs) {
            var product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> AppException.notFound("Товар " + req.getProductId()));

            BigDecimal price = req.getPrice() != null ? req.getPrice() : product.getPrice();
            BigDecimal total = price.multiply(req.getQuantity());

            result.add(OrderItem.builder()
                .orderId(orderId).productId(req.getProductId())
                .quantity(req.getQuantity()).price(price).totalPrice(total)
                .build());
        }
        return result;
    }

    private BigDecimal recalcTotal(UUID orderId) {
        return itemRepository.sumByOrderId(orderId);
    }

    private String uuid(UUID id) { return id != null ? id.toString() : null; }

    private Map<String, Object> queryStatusById(UUID statusId) {
        if (statusId == null) return Map.of();
        try {
            return jdbc.queryForMap("SELECT code, name, color FROM order_statuses WHERE id = ?", statusId);
        } catch (Exception e) { return Map.of(); }
    }

    private String queryCustomerName(UUID customerId) {
        if (customerId == null) return null;
        try {
            return jdbc.queryForObject(
                """
                SELECT COALESCE(pd.last_name || ' ' || pd.first_name, od.org_name)
                FROM customers c
                LEFT JOIN customer_personal_data pd ON pd.customer_id = c.id
                LEFT JOIN customer_org_data od ON od.customer_id = c.id
                WHERE c.id = ?
                """, String.class, customerId);
        } catch (Exception e) { return "—"; }
    }

    private String getUserName(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject(
                "SELECT last_name || ' ' || first_name FROM public.users WHERE id = ?",
                String.class, userId);
        } catch (Exception e) { return "—"; }
    }
}
