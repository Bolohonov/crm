package com.crm.rbac.config;

/**
 * Примеры использования @PreAuthorize в контроллерах и сервисах.
 *
 * Все проверки идут через SecurityExpressionService (бин "sec").
 *
 * ----------------------------------------------------------------
 * В контроллере (или сервисе):
 *
 *   // Требует конкретную пермиссию
 *   @PreAuthorize("@sec.has('CUSTOMER_VIEW')")
 *   public List<CustomerDto> getCustomers() { ... }
 *
 *   // Хотя бы одна из пермиссий
 *   @PreAuthorize("@sec.hasAny('TASK_EDIT', 'TASK_DELETE')")
 *   public void modifyTask(UUID id) { ... }
 *
 *   // Только администратор тенанта
 *   @PreAuthorize("@sec.isAdmin()")
 *   public void adminOnlyAction() { ... }
 *
 *   // Владелец ресурса ИЛИ имеет пермиссию
 *   @PreAuthorize("@sec.isOwnerOrHas(#task.authorId, 'TASK_EDIT')")
 *   public void editTask(@PathVariable UUID id, Task task) { ... }
 *
 *   // Владелец ресурса
 *   @PreAuthorize("@sec.isOwner(#userId)")
 *   public void viewMyProfile(UUID userId) { ... }
 *
 *   // Комбинирование стандартных Spring Security выражений
 *   @PreAuthorize("isAuthenticated() and @sec.has('ORDER_CREATE')")
 *   public OrderDto createOrder() { ... }
 *
 * ----------------------------------------------------------------
 * Использование Permissions-констант (чтобы не писать строки вручную):
 *
 *   import static com.crm.rbac.config.Permissions.*;
 *
 *   @PreAuthorize("@sec.has('" + CUSTOMER_EDIT + "')")
 *   // или через константу:
 *   @PreAuthorize("@sec.has(T(com.crm.rbac.config.Permissions).CUSTOMER_EDIT)")
 *
 * ----------------------------------------------------------------
 * На уровне метода сервиса (бизнес-логика):
 *
 *   @Service
 *   public class CustomerService {
 *
 *     @PreAuthorize("@sec.has('CUSTOMER_CREATE')")
 *     @Transactional
 *     public CustomerDto createCustomer(CreateCustomerRequest request) { ... }
 *
 *     @PreAuthorize("@sec.has('CUSTOMER_DELETE')")
 *     @Transactional
 *     public void deleteCustomer(UUID customerId) { ... }
 *   }
 *
 * ----------------------------------------------------------------
 * Проверка в коде без аннотации (если логика сложная):
 *
 *   @Autowired
 *   private SecurityExpressionService sec;
 *
 *   public void someMethod() {
 *     if (!sec.has(Permissions.CUSTOMER_EDIT)) {
 *       throw AppException.forbidden("Нет прав на редактирование клиента");
 *     }
 *     // ... логика
 *   }
 *
 * ----------------------------------------------------------------
 * Ответ при отсутствии прав:
 *   AccessDeniedException → GlobalExceptionHandler → 403 Forbidden
 *   { "success": false, "error": { "code": "ACCESS_DENIED", "message": "..." } }
 */
public final class PreAuthorizeExamples {
    // Этот класс — только документация, не инстанциируется
    private PreAuthorizeExamples() {}
}
