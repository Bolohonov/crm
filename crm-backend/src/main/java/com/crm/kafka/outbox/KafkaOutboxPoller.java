package com.crm.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Поллер Outbox-таблицы.
 *
 * Каждые 5 секунд берёт пачку PENDING-записей и отправляет их в Kafka.
 * При успехе — помечает как PUBLISHED.
 * При ошибке — инкрементирует счётчик, после 5 попыток помечает как FAILED.
 *
 * FOR UPDATE SKIP LOCKED позволяет безопасно запускать несколько экземпляров
 * приложения одновременно — записи не будут задублированы.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutboxPoller {

    private static final int BATCH_SIZE  = 50;
    private static final int MAX_PAYLOAD = 900_000; // Kafka default max 1MB

    private final KafkaOutboxRepository outboxRepository;
    /** String-value template — payload уже сериализован в JSON при сохранении в outbox. */
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval-ms:5000}")
    @Transactional
    public void poll() {
        List<KafkaOutbox> records = outboxRepository.findPendingForUpdate(BATCH_SIZE);
        if (records.isEmpty()) return;

        log.debug("Outbox poll: {} pending records", records.size());

        for (KafkaOutbox record : records) {
            try {
                publishRecord(record);
                outboxRepository.markPublished(record.getId(), Instant.now());
                log.debug("Outbox published: id={} topic={} key={}", record.getId(), record.getTopic(), record.getMessageKey());
            } catch (Exception ex) {
                log.error("Outbox publish failed: id={} attempt={} error={}",
                    record.getId(), record.getAttemptCount() + 1, ex.getMessage());
                outboxRepository.markAttemptFailed(record.getId(),
                    ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())) : "unknown");
            }
        }
    }

    private void publishRecord(KafkaOutbox record) throws Exception {
        // Payload уже сериализован в JSON при создании записи,
        // отправляем как строку — на стороне Kafka это просто байты
        if (record.getPayload().length() > MAX_PAYLOAD) {
            throw new IllegalStateException("Payload too large: " + record.getPayload().length() + " bytes");
        }

        // Используем send с ключом, ждём подтверждения (get с таймаутом)
        stringKafkaTemplate.send(record.getTopic(), record.getMessageKey(), record.getPayload())
            .get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
}
