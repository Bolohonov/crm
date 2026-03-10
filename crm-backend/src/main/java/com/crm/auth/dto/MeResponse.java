package com.crm.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ответ /auth/me — полная информация о текущем пользователе.
 *
 * Фронтенд использует этот ответ для:
 *  - инициализации Pinia auth store
 *  - построения sidebar (видимые модули)
 *  - проверки permissions перед рендерингом кнопок
 *
 * permissions[] — плоский массив кодов: ["CUSTOMER_VIEW", "TASK_CREATE", ...]
 * roles[]       — роли пользователя в текущем тенанте
 */
@Getter
@Setter
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {

    // ---- Пользователь ----
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String middleName;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private String userType;       // ADMIN | REGULAR

    // ---- Тенант ----
    private UUID tenantId;
    private String tenantSchema;
    private String tenantPlan;     // FREE | STANDARD

    // ---- RBAC ----
    private Set<String> permissions;  // ["CUSTOMER_VIEW", "TASK_CREATE", ...]
    private List<RoleInfo> roles;

    // ---- Настройки модулей (для sidebar) ----
    private Set<String> enabledModules;  // ["CUSTOMERS", "TASKS", "ORDERS", ...]

    @Getter
@Setter
@EqualsAndHashCode
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private UUID id;
        private String code;
        private String name;
    }
}
