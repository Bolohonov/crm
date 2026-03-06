package com.crm.kafka.producer;

import com.crm.kafka.config.KafkaProperties;
import com.crm.kafka.dto.TenantCreatedEvent;
import com.crm.kafka.outbox.KafkaOutbox;
import com.crm.kafka.outbox.KafkaOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCreatedProducer {

    private final KafkaOutboxRepository outboxRepository;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public void enqueue(UUID tenantId, String tenantSchema, String adminEmail) {
        TenantCreatedEvent event = TenantCreatedEvent.builder()
                .tenantId(tenantId)
                .tenantSchema(tenantSchema)
                .adminEmail(adminEmail)
                .activatedAt(Instant.now())
                .build();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TenantCreatedEvent", e);
        }

        outboxRepository.save(KafkaOutbox.builder()
                .id(UUID.randomUUID())
                .topic(kafkaProperties.getTopics().getCrmTenantCreated())
                .messageKey(tenantId.toString())
                .payload(payload)
                .status(KafkaOutbox.OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build());

        log.info("Outbox enqueued TenantCreated: tenantId={} schema={} adminEmail={}",
                tenantId, tenantSchema, adminEmail);
    }
}
