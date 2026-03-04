package com.crm.kafka.config;

import com.crm.kafka.dto.ShopOrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka:
 * - Топики (создаются автоматически если не существуют)
 * - Consumer factory с обработкой ошибок и DLQ
 * - Producer factory
 */
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties springKafkaProps;
    private final com.crm.kafka.config.KafkaProperties appKafkaProps;

    // ── Топики ────────────────────────────────────────────────────────

    @Bean
    public NewTopic topicShopOrderCreated() {
        return TopicBuilder.name(appKafkaProps.getTopics().getShopOrderCreated())
            .partitions(3)
            .replicas(1)   // в prod: 3
            .build();
    }

    @Bean
    public NewTopic topicCrmOrderStatusChanged() {
        return TopicBuilder.name(appKafkaProps.getTopics().getCrmOrderStatusChanged())
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic topicShopOrderCreatedDlq() {
        return TopicBuilder.name(appKafkaProps.getTopics().getShopOrderCreatedDlq())
            .partitions(1)
            .replicas(1)
            .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000L)) // 7 дней
            .build();
    }

    // ── Producer ──────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>(springKafkaProps.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Отдельный template для Outbox Poller — значение уже сериализовано в JSON-строку,
     * поэтому используем StringSerializer вместо JsonSerializer.
     */
    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        Map<String, Object> props = new HashMap<>(springKafkaProps.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    // ── Consumer ──────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, ShopOrderCreatedEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>(springKafkaProps.buildConsumerProperties(null));
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
            springKafkaProps.getConsumer().getGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        // ErrorHandlingDeserializer оборачивает JsonDeserializer — при ошибке
        // десериализации не крашит consumer, а передаёт исключение в ErrorHandler
        ErrorHandlingDeserializer<ShopOrderCreatedEvent> valueDeserializer =
            new ErrorHandlingDeserializer<>(
                new JsonDeserializer<>(ShopOrderCreatedEvent.class, false)
            );

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ShopOrderCreatedEvent>
    kafkaListenerContainerFactory(
        ConsumerFactory<String, ShopOrderCreatedEvent> consumerFactory,
        KafkaTemplate<String, Object> kafkaTemplate
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ShopOrderCreatedEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(2); // 2 потока = 2 партиции обрабатываются параллельно
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Повторные попытки + DLQ при окончательной ошибке
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new org.apache.kafka.common.TopicPartition(
                appKafkaProps.getTopics().getShopOrderCreatedDlq(), 0
            )
        );
        // 3 попытки с паузой 2 сек между ними, потом → DLQ
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));
        // Не ретраить ошибки десериализации — сразу в DLQ
        errorHandler.addNotRetryableExceptions(
            org.springframework.kafka.support.serializer.DeserializationException.class
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
