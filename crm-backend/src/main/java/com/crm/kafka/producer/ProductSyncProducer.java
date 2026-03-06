package com.crm.kafka.producer;

import com.crm.kafka.outbox.KafkaOutbox;
import com.crm.kafka.outbox.KafkaOutboxRepository;
import com.crm.product.entity.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Публикует изменения каталога товаров в Kafka для синхронизации с магазином.
 * Топик: crm.products.sync
 * Вызывается из ProductService при create/update/delete.
 */
@Slf4j @Service @RequiredArgsConstructor
public class ProductSyncProducer {
    private final KafkaOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueueSync(Product product, String action) {
        try {
            Map<String, Object> event = Map.of(
                    "productId", product.getId(),
                    "name", product.getName() != null ? product.getName() : "",
                    "description", product.getDescription() != null ? product.getDescription() : "",
                    "sku", product.getSku() != null ? product.getSku() : "",
                    "price", product.getPrice(),
                    "unit", product.getUnit() != null ? product.getUnit() : "шт",
                    "active", product.isActive(),
                    "action", action
            );
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(KafkaOutbox.builder()
                    .id(UUID.randomUUID())
                    .topic("crm.products.sync")
                    .messageKey(product.getSku() != null ? product.getSku() : product.getId().toString())
                    .payload(payload)
                    .status(KafkaOutbox.OutboxStatus.PENDING)
                    .createdAt(Instant.now())
                    .attemptCount(0)
                    .isNew(true)
                    .build());
            log.info("Product sync enqueued: sku={} action={}", product.getSku(), action);
        } catch (Exception e) {
            log.error("Failed to enqueue product sync: {}", e.getMessage(), e);
        }
    }
}
