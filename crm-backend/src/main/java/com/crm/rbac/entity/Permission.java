package com.crm.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Атомарное право доступа.
 * Код пермиссии: MODULE_ACTION, например CUSTOMER_VIEW, TASK_CREATE, USER_MANAGE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("permissions")
public class Permission {

    @Id
    private UUID id;

    private String code;         // CUSTOMER_VIEW, TASK_CREATE, ORDER_EDIT ...

    private String name;

    private String description;

    private String module;       // CUSTOMERS, TASKS, ORDERS, PRODUCTS, ADMIN, REPORTS
}
