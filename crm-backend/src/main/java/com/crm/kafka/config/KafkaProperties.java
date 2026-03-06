package com.crm.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Типизированные настройки Kafka-интеграции из application.yml (app.kafka.*).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    private Topics topics = new Topics();
    private String shopTenantSchema = "tenant_shop";
    private long outboxPollIntervalMs = 5000;

    @Data
    public static class Topics {
        private String shopOrderCreated      = "shop.orders.created";
        private String crmOrderStatusChanged = "crm.orders.status_changed";
        private String shopOrderCreatedDlq   = "shop.orders.created.dlq";
        private String crmTenantCreated      = "crm.tenant.created";
    }
}
