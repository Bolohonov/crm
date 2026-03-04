package com.crm.dashboard.service;

import com.crm.dashboard.dto.*;
import com.crm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final JdbcTemplate jdbc;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String[] MONTH_RU = {
            "", "Янв","Фев","Мар","Апр","Май","Июн",
            "Июл","Авг","Сен","Окт","Ноя","Дек"
    };

    public DashboardStatsDto getStats() {
        String schema = TenantContext.getCurrentSchema();
        setSchema(schema);

        long totalCustomers = count(schema, "customers", "is_active = true");
        long newCustomers = count(schema, "customers",
                "is_active = true AND created_at >= date_trunc('month', now())");
        long prevCustomers = count(schema, "customers",
                "is_active = true AND created_at >= date_trunc('month', now()) - interval '1 month' " +
                        "AND created_at < date_trunc('month', now())");
        int customerGrowth = growthPct(newCustomers, prevCustomers);

        Map<String, Object> orderStats = jdbc.queryForMap(
                "SELECT COUNT(*) AS cnt, " +
                        "COALESCE(SUM(total_amount),0) AS total, " +
                        "COALESCE(SUM(CASE WHEN created_at >= date_trunc('month',now()) THEN total_amount ELSE 0 END),0) AS this_month, " +
                        "COALESCE(SUM(CASE WHEN created_at >= date_trunc('month',now()) - interval '1 month' " +
                        "AND created_at < date_trunc('month',now()) THEN total_amount ELSE 0 END),0) AS prev_month, " +
                        "COALESCE(AVG(total_amount),0) AS avg_amount " +
                        "FROM " + schema + ".orders " +
                        "WHERE status_id NOT IN (SELECT id FROM " + schema + ".order_statuses WHERE code = 'CANCELLED')"
        );

        BigDecimal revenueThis = bd(orderStats.get("this_month"));
        BigDecimal revenuePrev = bd(orderStats.get("prev_month"));
        int revenueGrowth = growthPct(revenueThis.longValue(), revenuePrev.longValue());

        long totalTasks = count(schema, "tasks", "1=1");
        long overdue = count(schema, "tasks",
                "due_date < CURRENT_DATE AND completed_at IS NULL " +
                        "AND status_id NOT IN (SELECT id FROM " + schema + ".task_statuses WHERE is_final = true)");
        long dueToday = count(schema, "tasks",
                "due_date = CURRENT_DATE AND completed_at IS NULL");
        long completedThisWeek = count(schema, "tasks",
                "completed_at >= date_trunc('week', now())");

        long totalProducts = count(schema, "products", "1=1");
        long activeProducts = count(schema, "products", "is_active = true");

        return new DashboardStatsDto(
                new DashboardStatsDto.CustomerStats(totalCustomers, newCustomers, customerGrowth),
                new DashboardStatsDto.OrderStats(
                        ((Number) orderStats.get("cnt")).longValue(),
                        bd(orderStats.get("total")), revenueThis, revenueGrowth, bd(orderStats.get("avg_amount"))
                ),
                new DashboardStatsDto.TaskStats(totalTasks, overdue, dueToday, completedThisWeek),
                new DashboardStatsDto.ProductStats(totalProducts, activeProducts)
        );
    }

    public List<FunnelStageDto> getFunnel() {
        String schema = TenantContext.getCurrentSchema();
        setSchema(schema);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT os.code, os.name, os.color, os.sort_order, " +
                        "COUNT(o.id) AS cnt, COALESCE(SUM(o.total_amount), 0) AS total " +
                        "FROM " + schema + ".order_statuses os " +
                        "LEFT JOIN " + schema + ".orders o ON o.status_id = os.id " +
                        "GROUP BY os.id, os.code, os.name, os.color, os.sort_order " +
                        "ORDER BY os.sort_order"
        );

        long newCount = rows.stream()
                .filter(r -> "NEW".equals(r.get("code")))
                .mapToLong(r -> ((Number) r.get("cnt")).longValue())
                .findFirst().orElse(1L);

        List<FunnelStageDto> result = new ArrayList<>();
        long prevCount = newCount;
        for (var r : rows) {
            long cnt = ((Number) r.get("cnt")).longValue();
            int pct = newCount > 0 ? (int) Math.round(cnt * 100.0 / newCount) : 0;
            int conv = prevCount > 0 ? (int) Math.round(cnt * 100.0 / prevCount) : 0;
            boolean isFirst = "NEW".equals(r.get("code"));
            result.add(new FunnelStageDto(
                    (String) r.get("code"), (String) r.get("name"), (String) r.get("color"),
                    cnt, bd(r.get("total")), pct, isFirst ? 0 : conv
            ));
            if (!isFirst) prevCount = cnt;
        }
        return result;
    }

    public List<RevenuePointDto> getRevenue(int months) {
        String schema = TenantContext.getCurrentSchema();
        setSchema(schema);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT to_char(created_at, 'YYYY-MM') AS month, " +
                        "COALESCE(SUM(total_amount), 0) AS total, COUNT(*) AS cnt " +
                        "FROM " + schema + ".orders " +
                        "WHERE created_at >= date_trunc('month', now()) - interval '" + (months - 1) + " months' " +
                        "AND status_id NOT IN (SELECT id FROM " + schema + ".order_statuses WHERE code = 'CANCELLED') " +
                        "GROUP BY month ORDER BY month"
        );

        List<RevenuePointDto> result = new ArrayList<>();
        for (var r : rows) {
            String month = (String) r.get("month");
            result.add(new RevenuePointDto(month, monthLabel(month), bd(r.get("total")), ((Number) r.get("cnt")).longValue()));
        }
        return fillMissingMonths(result, months);
    }

    public List<OverdueTaskDto> getOverdueTasks(int limit) {
        String schema = TenantContext.getCurrentSchema();
        setSchema(schema);

        return jdbc.query(
                "SELECT t.id::text, t.title, t.priority, " +
                        "CURRENT_DATE - t.due_date AS days_overdue, " +
                        "COALESCE(c.company_name, c.last_name || ' ' || c.first_name) AS customer_name, " +
                        "COALESCE(u.last_name || ' ' || u.first_name, NULL) AS assignee_name " +
                        "FROM " + schema + ".tasks t " +
                        "LEFT JOIN " + schema + ".customers c ON c.id = t.customer_id " +
                        "LEFT JOIN " + schema + ".users u ON u.id = t.assignee_id " +
                        "WHERE t.due_date < CURRENT_DATE AND t.completed_at IS NULL " +
                        "AND t.status_id NOT IN (SELECT id FROM " + schema + ".task_statuses WHERE is_final = true) " +
                        "ORDER BY days_overdue DESC, " +
                        "CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END " +
                        "LIMIT " + limit,
                (rs, i) -> new OverdueTaskDto(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getString(5), rs.getString(6)
                )
        );
    }

    public List<RecentActivityDto> getRecentActivity(int limit) {
        String schema = TenantContext.getCurrentSchema();
        setSchema(schema);

        return jdbc.query(
                "SELECT id::text, type, description, entity_id::text, entity_type, created_at, user_name " +
                        "FROM ( " +
                        "SELECT o.id, 'ORDER_CREATED' AS type, " +
                        "'Создан заказ: ' || COALESCE(p.name, 'заказ') || ' (' || " +
                        "CASE WHEN o.total_amount >= 1000000 THEN '₽' || ROUND(o.total_amount/1000000.0,1) || 'М' " +
                        "WHEN o.total_amount >= 1000 THEN '₽' || ROUND(o.total_amount/1000.0) || 'К' " +
                        "ELSE '₽' || o.total_amount::text END || ')' AS description, " +
                        "o.id AS entity_id, 'ORDER' AS entity_type, o.created_at, " +
                        "COALESCE(u.last_name || ' ' || LEFT(u.first_name,1) || '.', NULL) AS user_name " +
                        "FROM " + schema + ".orders o " +
                        "LEFT JOIN " + schema + ".order_items oi ON oi.order_id = o.id AND oi.id = (SELECT id FROM " + schema + ".order_items WHERE order_id = o.id LIMIT 1) " +
                        "LEFT JOIN " + schema + ".products p ON p.id = oi.product_id " +
                        "LEFT JOIN " + schema + ".users u ON u.id = o.author_id " +
                        "UNION ALL " +
                        "SELECT t.id, 'TASK_COMPLETED', 'Задача выполнена: ' || LEFT(t.title, 60), " +
                        "t.id, 'TASK', t.completed_at, " +
                        "COALESCE(u.last_name || ' ' || LEFT(u.first_name,1) || '.', NULL) " +
                        "FROM " + schema + ".tasks t " +
                        "LEFT JOIN " + schema + ".users u ON u.id = t.assignee_id " +
                        "WHERE t.completed_at IS NOT NULL " +
                        "UNION ALL " +
                        "SELECT c.id, 'CUSTOMER_CREATED', " +
                        "'Новый клиент: ' || COALESCE(c.company_name, c.last_name || ' ' || c.first_name), " +
                        "c.id, 'CUSTOMER', c.created_at, NULL " +
                        "FROM " + schema + ".customers c " +
                        ") events ORDER BY created_at DESC LIMIT " + limit,
                (rs, i) -> new RecentActivityDto(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant(),
                        rs.getString(7)
                )
        );
    }

    public List<TopCustomerDto> getTopCustomers(int limit) {
        String schema = TenantContext.getCurrentSchema();
        setSchema(schema);

        return jdbc.query(
                "SELECT c.id::text, " +
                        "COALESCE(c.company_name, c.last_name || ' ' || c.first_name), " +
                        "c.type, COUNT(o.id), COALESCE(SUM(o.total_amount), 0) " +
                        "FROM " + schema + ".customers c " +
                        "JOIN " + schema + ".orders o ON o.customer_id = c.id " +
                        "WHERE o.status_id NOT IN (SELECT id FROM " + schema + ".order_statuses WHERE code = 'CANCELLED') " +
                        "GROUP BY c.id, c.company_name, c.last_name, c.first_name, c.type " +
                        "ORDER BY SUM(o.total_amount) DESC LIMIT " + limit,
                (rs, i) -> new TopCustomerDto(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getBigDecimal(5)
                )
        );
    }

    private void setSchema(String schema) {
        jdbc.execute("SET search_path TO " + schema + ", public");
    }

    private long count(String schema, String table, String where) {
        Long res = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + schema + "." + table + " WHERE " + where, Long.class);
        return res != null ? res : 0L;
    }

    private BigDecimal bd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private int growthPct(long current, long prev) {
        if (prev == 0) return current > 0 ? 100 : 0;
        return (int) Math.round((current - prev) * 100.0 / prev);
    }

    private String monthLabel(String yearMonth) {
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