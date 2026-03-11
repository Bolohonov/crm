package com.crm.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoSchemaInitializer {

    private static final String DEMO_SCHEMA = "tenant_demo";
    private final TenantSchemaService tenantSchemaService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (tenantSchemaService.schemaExists(DEMO_SCHEMA)) {
            log.info("Demo schema already exists, skipping provisioning");
            return;
        }
        log.info("Provisioning demo schema...");
        tenantSchemaService.provisionDemoSchema(DEMO_SCHEMA);
        tenantSchemaService.seedAdminUser(
                DEMO_SCHEMA,
                UUID.fromString("00000000-0000-0000-0000-000000000001"), // DEMO_USER_ID
                "demo@crm.local",
                "Демо",
                "Пользователь",
                null
        );
        log.info("Demo schema provisioned successfully");
    }
}