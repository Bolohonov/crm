package com.crm.rbac.entity;

import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Атомарное право доступа.
 * Код пермиссии: MODULE_ACTION, например CUSTOMER_VIEW, TASK_CREATE, USER_MANAGE.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table("permissions")
public class Permission {
    @EqualsAndHashCode.Include
    @Id
    private UUID id;

    private String code;         // CUSTOMER_VIEW, TASK_CREATE, ORDER_EDIT ...

    private String name;

    private String description;

    private String module;       // CUSTOMERS, TASKS, ORDERS, PRODUCTS, ADMIN, REPORTS
}
