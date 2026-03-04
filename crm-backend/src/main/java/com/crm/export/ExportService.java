package com.crm.export;

import com.crm.customer.entity.Customer;
import com.crm.customer.repository.CustomerRepository;
import com.crm.order.entity.Order;
import com.crm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Сервис экспорта данных в Excel (.xlsx) и CSV.
 *
 * Поддерживает фильтрацию:
 *   - по менеджеру (authorId / assigneeId)
 *   - по статусу заказа
 *
 * Использует Apache POI для Excel и собственную сборку строк для CSV.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    private static final DateTimeFormatter D_FMT =
        DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Moscow"));

    private final CustomerRepository customerRepository;
    private final OrderRepository    orderRepository;
    private final JdbcTemplate       jdbc;

    // ══════════════════════════════════════════════════════════════════
    //  КЛИЕНТЫ
    // ══════════════════════════════════════════════════════════════════

    public byte[] exportCustomersExcel(UUID managerId) throws IOException {
        List<CustomerRow> rows = loadCustomerRows(managerId);
        return buildCustomersExcel(rows);
    }

    public byte[] exportCustomersCsv(UUID managerId) {
        List<CustomerRow> rows = loadCustomerRows(managerId);
        return buildCustomersCsv(rows);
    }

    private List<CustomerRow> loadCustomerRows(UUID managerId) {
        String sql = """
            SELECT
                c.id,
                c.type,
                c.status,
                c.created_at,
                pd.last_name,
                pd.first_name,
                pd.middle_name,
                pd.email,
                pd.phone,
                pd.address,
                od.org_name,
                od.inn,
                od.kpp,
                od.ogrn,
                u.last_name  AS manager_last,
                u.first_name AS manager_first,
                (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.id) AS order_count,
                (SELECT COALESCE(SUM(o.total_amount),0) FROM orders o WHERE o.customer_id = c.id) AS total_revenue
            FROM customers c
            LEFT JOIN customer_personal_data pd ON pd.customer_id = c.id
            LEFT JOIN customer_org_data      od ON od.customer_id = c.id
            LEFT JOIN users                  u  ON u.id = c.created_by
            WHERE (:managerId::uuid IS NULL OR c.created_by = :managerId::uuid)
            ORDER BY c.created_at DESC
            """;

        return jdbc.query(
            managerId != null
                ? sql.replace(":managerId::uuid IS NULL OR c.created_by = :managerId::uuid",
                               "c.created_by = '" + managerId + "'")
                : sql.replace(":managerId::uuid IS NULL OR c.created_by = :managerId::uuid",
                               "true"),
            (rs, i) -> new CustomerRow(
                rs.getString("id"),
                rs.getString("type"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("last_name"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("org_name"),
                rs.getString("inn"),
                rs.getString("kpp"),
                rs.getString("ogrn"),
                fullName(rs.getString("manager_last"), rs.getString("manager_first")),
                rs.getLong("order_count"),
                rs.getBigDecimal("total_revenue")
            )
        );
    }

    private byte[] buildCustomersExcel(List<CustomerRow> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Клиенты");

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle moneyStyle  = createMoneyStyle(wb);
            CellStyle dateStyle   = createDateStyle(wb);

            // Заголовок
            String[] headers = {
                "ID", "Тип", "Статус", "Дата создания",
                "Фамилия", "Имя", "Отчество", "Email", "Телефон", "Адрес",
                "Организация", "ИНН", "КПП", "ОГРН",
                "Менеджер", "Кол-во заказов", "Сумма заказов"
            };
            Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Данные
            for (int r = 0; r < rows.size(); r++) {
                CustomerRow d = rows.get(r);
                Row row = sheet.createRow(r + 1);
                int c = 0;
                setCell(row, c++, d.id());
                setCell(row, c++, "INDIVIDUAL".equals(d.type()) ? "Физ. лицо" : "Юр. лицо");
                setCell(row, c++, d.status());
                setDateCell(row, c++, d.createdAt(), dateStyle);
                setCell(row, c++, d.lastName());
                setCell(row, c++, d.firstName());
                setCell(row, c++, d.middleName());
                setCell(row, c++, d.email());
                setCell(row, c++, d.phone());
                setCell(row, c++, d.address());
                setCell(row, c++, d.orgName());
                setCell(row, c++, d.inn());
                setCell(row, c++, d.kpp());
                setCell(row, c++, d.ogrn());
                setCell(row, c++, d.managerName());
                setNumberCell(row, c++, d.orderCount());
                setMoneyCell(row, c++, d.totalRevenue(), moneyStyle);
            }

            // Автоширина
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            // Итоговая строка
            Row totalRow = sheet.createRow(rows.size() + 1);
            CellStyle totalStyle = createTotalStyle(wb);
            Cell totalLabel = totalRow.createCell(15);
            totalLabel.setCellValue("ИТОГО:");
            totalLabel.setCellStyle(totalStyle);
            BigDecimal sum = rows.stream()
                .map(r2 -> r2.totalRevenue() != null ? r2.totalRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            Cell totalVal = totalRow.createCell(16);
            totalVal.setCellValue(sum.doubleValue());
            totalVal.setCellStyle(moneyStyle);

            // Freeze первую строку
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildCustomersCsv(List<CustomerRow> rows) {
        StringBuilder sb = new StringBuilder('\uFEFF'); // BOM для Excel
        sb.append("ID;Тип;Статус;Дата создания;Фамилия;Имя;Отчество;Email;Телефон;Адрес;");
        sb.append("Организация;ИНН;КПП;ОГРН;Менеджер;Кол-во заказов;Сумма заказов\n");

        for (CustomerRow r : rows) {
            sb.append(csv(r.id())).append(';')
              .append(csv("INDIVIDUAL".equals(r.type()) ? "Физ. лицо" : "Юр. лицо")).append(';')
              .append(csv(r.status())).append(';')
              .append(r.createdAt() != null ? D_FMT.format(r.createdAt()) : "").append(';')
              .append(csv(r.lastName())).append(';')
              .append(csv(r.firstName())).append(';')
              .append(csv(r.middleName())).append(';')
              .append(csv(r.email())).append(';')
              .append(csv(r.phone())).append(';')
              .append(csv(r.address())).append(';')
              .append(csv(r.orgName())).append(';')
              .append(csv(r.inn())).append(';')
              .append(csv(r.kpp())).append(';')
              .append(csv(r.ogrn())).append(';')
              .append(csv(r.managerName())).append(';')
              .append(r.orderCount()).append(';')
              .append(r.totalRevenue() != null ? r.totalRevenue().toPlainString() : "0")
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════════════
    //  ЗАКАЗЫ
    // ══════════════════════════════════════════════════════════════════

    public byte[] exportOrdersExcel(UUID managerId, String statusCode) throws IOException {
        List<OrderRow> rows = loadOrderRows(managerId, statusCode);
        return buildOrdersExcel(rows);
    }

    public byte[] exportOrdersCsv(UUID managerId, String statusCode) {
        List<OrderRow> rows = loadOrderRows(managerId, statusCode);
        return buildOrdersCsv(rows);
    }

    private List<OrderRow> loadOrderRows(UUID managerId, String statusCode) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (managerId != null)  where.append(" AND o.author_id = '").append(managerId).append("'");
        if (statusCode != null) where.append(" AND s.code = '").append(statusCode.replace("'", "")).append("'");

        String sql = """
            SELECT
                o.id,
                o.external_order_id,
                o.created_at,
                o.updated_at,
                o.total_amount,
                o.comment,
                s.name   AS status_name,
                s.code   AS status_code,
                pd.last_name  AS cust_last,
                pd.first_name AS cust_first,
                pd.email      AS cust_email,
                pd.phone      AS cust_phone,
                ua.last_name  AS author_last,
                ua.first_name AS author_first,
                (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
            FROM orders o
            JOIN order_statuses      s  ON s.id  = o.status_id
            JOIN customers           c  ON c.id  = o.customer_id
            LEFT JOIN customer_personal_data pd ON pd.customer_id = c.id
            LEFT JOIN users          ua ON ua.id = o.author_id
            %s
            ORDER BY o.created_at DESC
            """.formatted(where);

        return jdbc.query(sql, (rs, i) -> new OrderRow(
            rs.getString("id"),
            rs.getString("external_order_id"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            rs.getBigDecimal("total_amount"),
            rs.getString("comment"),
            rs.getString("status_name"),
            rs.getString("status_code"),
            fullName(rs.getString("cust_last"), rs.getString("cust_first")),
            rs.getString("cust_email"),
            rs.getString("cust_phone"),
            fullName(rs.getString("author_last"), rs.getString("author_first")),
            rs.getLong("item_count")
        ));
    }

    private byte[] buildOrdersExcel(List<OrderRow> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Заказы");

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle moneyStyle  = createMoneyStyle(wb);
            CellStyle dateStyle   = createDateStyle(wb);

            String[] headers = {
                "ID CRM", "Номер в магазине", "Дата создания", "Дата обновления",
                "Клиент", "Email клиента", "Телефон клиента",
                "Статус", "Менеджер", "Кол-во позиций", "Сумма", "Комментарий"
            };
            Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            for (int r = 0; r < rows.size(); r++) {
                OrderRow d = rows.get(r);
                Row row = sheet.createRow(r + 1);
                int c = 0;
                setCell(row, c++, d.id());
                setCell(row, c++, d.externalOrderId());
                setDateCell(row, c++, d.createdAt(), dateStyle);
                setDateCell(row, c++, d.updatedAt(), dateStyle);
                setCell(row, c++, d.customerName());
                setCell(row, c++, d.customerEmail());
                setCell(row, c++, d.customerPhone());
                setCell(row, c++, d.statusName());
                setCell(row, c++, d.managerName());
                setNumberCell(row, c++, d.itemCount());
                setMoneyCell(row, c++, d.totalAmount(), moneyStyle);
                setCell(row, c++, d.comment());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            // Итог
            Row totalRow = sheet.createRow(rows.size() + 1);
            CellStyle totalStyle = createTotalStyle(wb);
            Cell totalLabel = totalRow.createCell(9);
            totalLabel.setCellValue("ИТОГО:");
            totalLabel.setCellStyle(totalStyle);
            BigDecimal sum = rows.stream()
                .map(r2 -> r2.totalAmount() != null ? r2.totalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            Cell totalVal = totalRow.createCell(10);
            totalVal.setCellValue(sum.doubleValue());
            totalVal.setCellStyle(moneyStyle);

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildOrdersCsv(List<OrderRow> rows) {
        StringBuilder sb = new StringBuilder('\uFEFF');
        sb.append("ID CRM;Номер в магазине;Дата создания;Дата обновления;");
        sb.append("Клиент;Email клиента;Телефон клиента;Статус;Менеджер;Кол-во позиций;Сумма;Комментарий\n");

        for (OrderRow r : rows) {
            sb.append(csv(r.id())).append(';')
              .append(csv(r.externalOrderId())).append(';')
              .append(r.createdAt() != null ? DT_FMT.format(r.createdAt()) : "").append(';')
              .append(r.updatedAt() != null ? DT_FMT.format(r.updatedAt()) : "").append(';')
              .append(csv(r.customerName())).append(';')
              .append(csv(r.customerEmail())).append(';')
              .append(csv(r.customerPhone())).append(';')
              .append(csv(r.statusName())).append(';')
              .append(csv(r.managerName())).append(';')
              .append(r.itemCount()).append(';')
              .append(r.totalAmount() != null ? r.totalAmount().toPlainString() : "0").append(';')
              .append(csv(r.comment()))
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Утилиты POI
    // ══════════════════════════════════════════════════════════════════

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle createMoneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00 ₽"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private CellStyle createDateStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("dd.mm.yyyy"));
        return s;
    }

    private CellStyle createTotalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private void setCell(Row row, int col, String val) {
        row.createCell(col).setCellValue(val != null ? val : "");
    }

    private void setNumberCell(Row row, int col, long val) {
        row.createCell(col, CellType.NUMERIC).setCellValue(val);
    }

    private void setMoneyCell(Row row, int col, BigDecimal val, CellStyle style) {
        Cell c = row.createCell(col, CellType.NUMERIC);
        c.setCellValue(val != null ? val.doubleValue() : 0);
        c.setCellStyle(style);
    }

    private void setDateCell(Row row, int col, Instant instant, CellStyle style) {
        Cell c = row.createCell(col);
        if (instant != null) {
            c.setCellValue(java.util.Date.from(instant));
            c.setCellStyle(style);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Утилиты общие
    // ══════════════════════════════════════════════════════════════════

    private String csv(String val) {
        if (val == null) return "";
        // Экранируем кавычки и оборачиваем если содержит разделитель
        if (val.contains(";") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private String fullName(String last, String first) {
        if (last == null && first == null) return "";
        if (first == null) return last;
        if (last == null)  return first;
        return last + " " + first;
    }

    private Instant toInstant(java.sql.Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Record DTO (внутренние)
    // ══════════════════════════════════════════════════════════════════

    record CustomerRow(
        String id, String type, String status, Instant createdAt,
        String lastName, String firstName, String middleName,
        String email, String phone, String address,
        String orgName, String inn, String kpp, String ogrn,
        String managerName, long orderCount, BigDecimal totalRevenue
    ) {}

    record OrderRow(
        String id, String externalOrderId,
        Instant createdAt, Instant updatedAt,
        BigDecimal totalAmount, String comment,
        String statusName, String statusCode,
        String customerName, String customerEmail, String customerPhone,
        String managerName, long itemCount
    ) {}
}
