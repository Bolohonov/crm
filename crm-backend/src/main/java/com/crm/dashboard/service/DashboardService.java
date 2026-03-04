package com.crm.dashboard.service;

import com.crm.dashboard.dto.*;
import com.crm.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    @PersistenceContext
    private EntityManager em;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String[] MONTH_RU = {
        "", "Янв","Фев","Мар","Апр","Май","Июн",
        "Июл","Авг","Сен","Окт","Ноя","Дек"
    };

    // ── Сводная статистика ─────────────────────────────────────────
    public DashboardStatsDto getStats() {
        String schema = TenantContext.getCurrentSchema();

        // Клиенты
        long totalCustomers = count(schema, "customers", "is_active = true");
        long newCustomers   = count(schema, "customers",
            "is_active = true AND created_at >= date_trunc('month', now())");

        long prevCustomers  = count(schema, "customers",
            "is_active = true AND created_at >= date_trunc('month', now()) - interval '1 month' " +
            "AND created_at < date_trunc('month', now())");
        int customerGrowth  = growthPct(newCustomers, prevCustomers);

        // Заказы
        Object[] orderStats = (Object[]) em.createNativeQuery(
            "SELECT COUNT(*), " +
            "       COALESCE(SUM(total_amount),0), " +
            "       COALESCE(SUM(CASE WHEN created_at >= date_trunc('month',now()) THEN total_amount ELSE 0 END),0), " +
            "       COALESCE(SUM(CASE WHEN created_at >= date_trunc('month',now()) - interval '1 month' " +
            "                         AND created_at < date_trunc('month',now()) THEN total_amount ELSE 0 END),0), " +
            "       COALESCE(AVG(total_amount),0) " +
            "FROM " + schema + ".orders " +
            "WHERE status_id NOT IN (SELECT id FROM " + schema + ".order_statuses WHERE code = 'CANCELLED')"
        ).getSingleResult();

        BigDecimal revenueThis = bd(orderStats[2]);
        BigDecimal revenuePrev = bd(orderStats[3]);
        int revenueGrowth = growthPct(revenueThis.longValue(), revenuePrev.longValue());

        // Задачи
        long totalTasks = count(schema, "tasks", "1=1");
        long overdue    = count(schema, "tasks",
            "due_date < CURRENT_DATE AND completed_at IS NULL " +
            "AND status_id NOT IN (SELECT id FROM " + schema + ".task_statuses WHERE is_final = true)");
        long dueToday   = count(schema, "tasks",
            "due_date = CURRENT_DATE AND completed_at IS NULL");
        long completedThisWeek = count(schema, "tasks",
            "completed_at >= date_trunc('week', now())");

        // Продукты
        long totalProducts  = count(schema, "products", "1=1");
        long activeProducts = count(schema, "products", "is_active = true");

        return new DashboardStatsDto(
            new DashboardStatsDto.CustomerStats(totalCustomers, newCustomers, customerGrowth),
            new DashboardStatsDto.OrderStats(
                ((Number) orderStats[0]).longValue(),
                bd(orderStats[1]), revenueThis, revenueGrowth, bd(orderStats[4])
            ),
            new DashboardStatsDto.TaskStats(totalTasks, overdue, dueToday, completedThisWeek),
            new DashboardStatsDto.ProductStats(totalProducts, activeProducts)
        );
    }

    // ── Воронка продаж ─────────────────────────────────────────────
    public List<FunnelStageDto> getFunnel() {
        String schema = TenantContext.getCurrentSchema();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT os.code, os.name, os.color, os.sort_order, " +
            "       COUNT(o.id) AS cnt, " +
            "       COALESCE(SUM(o.total_amount), 0) AS total " +
            "FROM " + schema + ".order_statuses os " +
            "LEFT JOIN " + schema + ".orders o ON o.status_id = os.id " +
            "GROUP BY os.id, os.code, os.name, os.color, os.sort_order " +
            "ORDER BY os.sort_order"
        ).getResultList();

        // Считаем pct относительно NEW (первый этап)
        long newCount = rows.stream()
            .filter(r -> "NEW".equals(r[0]))
            .mapToLong(r -> ((Number) r[4]).longValue())
            .findFirst().orElse(1L);

        List<FunnelStageDto> result = new ArrayList<>();
        long prevCount = newCount;
        for (Object[] r : rows) {
            long cnt  = ((Number) r[4]).longValue();
            int  pct  = newCount > 0 ? (int) Math.round(cnt * 100.0 / newCount) : 0;
            int  conv = prevCount > 0 ? (int) Math.round(cnt * 100.0 / prevCount) : 0;

            // Для первого этапа conversionPct = 0
            boolean isFirst = "NEW".equals(r[0]);
            result.add(new FunnelStageDto(
                (String) r[0], (String) r[1], (String) r[2],
                cnt, bd(r[5]), pct, isFirst ? 0 : conv
            ));
            if (!isFirst) prevCount = cnt;
        }
        return result;
    }

    // ── Выручка по месяцам ─────────────────────────────────────────
    public List<RevenuePointDto> getRevenue(int months) {
        String schema = TenantContext.getCurrentSchema();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT to_char(created_at, 'YYYY-MM') AS month, " +
            "       COALESCE(SUM(total_amount), 0), " +
            "       COUNT(*) " +
            "FROM " + schema + ".orders " +
            "WHERE created_at >= date_trunc('month', now()) - interval '" + (months - 1) + " months' " +
            "  AND status_id NOT IN (SELECT id FROM " + schema + ".order_statuses WHERE code = 'CANCELLED') " +
            "GROUP BY month " +
            "ORDER BY month"
        ).getResultList();

        List<RevenuePointDto> result = new ArrayList<>();
        for (Object[] r : rows) {
            String month = (String) r[0];
            String label = monthLabel(month);
            result.add(new RevenuePointDto(month, label, bd(r[1]), ((Number) r[2]).longValue()));
        }

        // Заполняем пропущенные месяцы нулями
        return fillMissingMonths(result, months);
    }

    // ── Просроченные задачи ────────────────────────────────────────
    public List<OverdueTaskDto> getOverdueTasks(int limit) {
        String schema = TenantContext.getCurrentSchema();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT t.id::text, t.title, t.priority, " +
            "       CURRENT_DATE - t.due_date AS days_overdue, " +
            "       COALESCE(c.company_name, c.last_name || ' ' || c.first_name) AS customer_name, " +
            "       COALESCE(u.last_name || ' ' || u.first_name, NULL) AS assignee_name " +
            "FROM " + schema + ".tasks t " +
            "LEFT JOIN " + schema + ".customers c ON c.id = t.customer_id " +
            "LEFT JOIN " + schema + ".users u ON u.id = t.assignee_id " +
            "WHERE t.due_date < CURRENT_DATE " +
            "  AND t.completed_at IS NULL " +
            "  AND t.status_id NOT IN (SELECT id FROM " + schema + ".task_statuses WHERE is_final = true) " +
            "ORDER BY days_overdue DESC, " +
            "         CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END " +
            "LIMIT " + limit
        ).getResultList();

        return rows.stream().map(r -> new OverdueTaskDto(
            (String) r[0], (String) r[1], (String) r[2],
            ((Number) r[3]).intValue(),
            (String) r[4], (String) r[5]
        )).toList();
    }

    // ── Лента активности ───────────────────────────────────────────
    public List<RecentActivityDto> getRecentActivity(int limit) {
        String schema = TenantContext.getCurrentSchema();

        // Объединяем события из orders и tasks через UNION
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT id::text, type, description, entity_id::text, entity_type, created_at, user_name " +
            "FROM ( " +
            "  SELECT o.id, 'ORDER_CREATED' AS type, " +
            "         'Создан заказ: ' || COALESCE(p.name, 'заказ') || ' (' || " +
            "         CASE WHEN o.total_amount >= 1000000 THEN '₽' || ROUND(o.total_amount/1000000.0,1) || 'М' " +
            "              WHEN o.total_amount >= 1000 THEN '₽' || ROUND(o.total_amount/1000.0) || 'К' " +
            "              ELSE '₽' || o.total_amount::text END || ')' AS description, " +
            "         o.id AS entity_id, 'ORDER' AS entity_type, o.created_at, " +
            "         COALESCE(u.last_name || ' ' || LEFT(u.first_name,1) || '.', NULL) AS user_name " +
            "  FROM " + schema + ".orders o " +
            "  LEFT JOIN " + schema + ".order_items oi ON oi.order_id = o.id AND oi.id = (SELECT id FROM " + schema + ".order_items WHERE order_id = o.id LIMIT 1) " +
            "  LEFT JOIN " + schema + ".products p ON p.id = oi.product_id " +
            "  LEFT JOIN " + schema + ".users u ON u.id = o.author_id " +
            "  UNION ALL " +
            "  SELECT t.id, 'TASK_COMPLETED', " +
            "         'Задача выполнена: ' || LEFT(t.title, 60), " +
            "         t.id, 'TASK', t.completed_at, " +
            "         COALESCE(u.last_name || ' ' || LEFT(u.first_name,1) || '.', NULL) " +
            "  FROM " + schema + ".tasks t " +
            "  LEFT JOIN " + schema + ".users u ON u.id = t.assignee_id " +
            "  WHERE t.completed_at IS NOT NULL " +
            "  UNION ALL " +
            "  SELECT c.id, 'CUSTOMER_CREATED', " +
            "         'Новый клиент: ' || COALESCE(c.company_name, c.last_name || ' ' || c.first_name), " +
            "         c.id, 'CUSTOMER', c.created_at, NULL " +
            "  FROM " + schema + ".customers c " +
            ") events " +
            "ORDER BY created_at DESC " +
            "LIMIT " + limit
        ).getResultList();

        return rows.stream().map(r -> new RecentActivityDto(
            (String) r[0], (String) r[1], (String) r[2],
            (String) r[3], (String) r[4],
            ((java.sql.Timestamp) r[5]).toInstant(),
            (String) r[6]
        )).toList();
    }

    // ── Топ клиентов ───────────────────────────────────────────────
    public List<TopCustomerDto> getTopCustomers(int limit) {
        String schema = TenantContext.getCurrentSchema();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT c.id::text, " +
            "       COALESCE(c.company_name, c.last_name || ' ' || c.first_name), " +
            "       c.type, COUNT(o.id), COALESCE(SUM(o.total_amount), 0) " +
            "FROM " + schema + ".customers c " +
            "JOIN " + schema + ".orders o ON o.customer_id = c.id " +
            "WHERE o.status_id NOT IN (SELECT id FROM " + schema + ".order_statuses WHERE code = 'CANCELLED') " +
            "GROUP BY c.id, c.company_name, c.last_name, c.first_name, c.type " +
            "ORDER BY SUM(o.total_amount) DESC " +
            "LIMIT " + limit
        ).getResultList();

        return rows.stream().map(r -> new TopCustomerDto(
            (String) r[0], (String) r[1], (String) r[2],
            ((Number) r[3]).longValue(), bd(r[4])
        )).toList();
    }

    // ── Утилиты ───────────────────────────────────────────────────
    private long count(String schema, String table, String where) {
        Object res = em.createNativeQuery(
            "SELECT COUNT(*) FROM " + schema + "." + table + " WHERE " + where
        ).getSingleResult();
        return ((Number) res).longValue();
    }

    private BigDecimal bd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private int growthPct(long current, long prev) {
        if (prev == 0) return current > 0 ? 100 : 0;
        return (int) Math.round((current - prev) * 100.0 / prev);
    }

    private String monthLabel(String yearMonth) {
        // "2026-02" → "Фев"
        int month = Integer.parseInt(yearMonth.substring(5));
        return MONTH_RU[month];
    }

    private List<RevenuePointDto> fillMissingMonths(List<RevenuePointDto> data, int months) {
        Map<String, RevenuePointDto> map = new LinkedHashMap<>();
        for (RevenuePointDto p : data) map.put(p.month(), p);

        List<RevenuePointDto> result = new ArrayList<>();
        YearMonth current = YearMonth.now().minusMonths(months - 1);
        for (int i = 0; i < months; i++) {
            String key = current.format(MONTH_FMT);
            result.add(map.getOrDefault(key,
                new RevenuePointDto(key, monthLabel(key), BigDecimal.ZERO, 0L)));
            current = current.plusMonths(1);
        }
        return result;
    }
}
