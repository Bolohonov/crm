package com.crm.kafka;

import com.crm.kafka.config.KafkaProperties;
import com.crm.kafka.dto.OrderStatusChangedEvent;
import com.crm.kafka.outbox.KafkaOutbox;
import com.crm.kafka.outbox.KafkaOutboxRepository;
import com.crm.kafka.producer.OrderStatusChangedProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStatusChangedProducerTest {

    @Mock
    private KafkaOutboxRepository outboxRepository;

    private OrderStatusChangedProducer producer;

    private static final String TEST_TOPIC = "crm.orders.status_changed";

    @BeforeEach
    void setUp() {
        KafkaProperties props = new KafkaProperties();
        props.getTopics().setCrmOrderStatusChanged(TEST_TOPIC);
        producer = new OrderStatusChangedProducer(outboxRepository, props, new ObjectMapper());
    }

    @Test
    @DisplayName("Не публикует в Outbox при переходе в статус NEW")
    void shouldSkipNewStatus() {
        producer.enqueue(UUID.randomUUID(), "SHOP-001", UUID.randomUUID(),
            null, "NEW", "system", null);

        verifyNoInteractions(outboxRepository);
    }

    @Test
    @DisplayName("Сохраняет запись в Outbox при переходе в PICKING")
    void shouldEnqueueForPickingStatus() {
        UUID crmOrderId    = UUID.randomUUID();
        UUID shopOrderUuid = UUID.randomUUID();

        producer.enqueue(crmOrderId, "SHOP-00042", shopOrderUuid,
            "NEW", "PICKING", "Иванов И.", "Принято в работу");

        var captor = ArgumentCaptor.forClass(KafkaOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        KafkaOutbox saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo(TEST_TOPIC);
        assertThat(saved.getStatus()).isEqualTo(KafkaOutbox.OutboxStatus.PENDING);
        assertThat(saved.getMessageKey()).isEqualTo(shopOrderUuid.toString());
        assertThat(saved.getPayload()).contains("PICKING");
        assertThat(saved.getPayload()).contains("SHOP-00042");
        assertThat(saved.getAttemptCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Сохраняет запись в Outbox при переходе в SHIPPED")
    void shouldEnqueueForShippedStatus() {
        UUID crmOrderId    = UUID.randomUUID();
        UUID shopOrderUuid = UUID.randomUUID();

        producer.enqueue(crmOrderId, "SHOP-00099", shopOrderUuid,
            "PICKING", "SHIPPED", "Петров С.", "СДЭК: 12345678");

        var captor = ArgumentCaptor.forClass(KafkaOutbox.class);
        verify(outboxRepository).save(captor.capture());

        KafkaOutbox saved = captor.getValue();
        assertThat(saved.getPayload()).contains("\"newStatus\":\"SHIPPED\"");
        assertThat(saved.getPayload()).contains("\"previousStatus\":\"PICKING\"");
        assertThat(saved.getPayload()).contains("СДЭК");
    }

    @Test
    @DisplayName("Использует crmOrderId как ключ если shopOrderUuid не задан (ручной заказ)")
    void shouldUseCrmOrderIdAsKeyWhenNoShopUuid() {
        UUID crmOrderId = UUID.randomUUID();

        producer.enqueue(crmOrderId, null, null,
            "PICKING", "SHIPPED", "system", null);

        var captor = ArgumentCaptor.forClass(KafkaOutbox.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getMessageKey()).isEqualTo(crmOrderId.toString());
    }

    @Test
    @DisplayName("Payload содержит корректный JSON с crmOrderId")
    void shouldSerializeEventCorrectly() throws Exception {
        UUID crmOrderId    = UUID.randomUUID();
        UUID shopOrderUuid = UUID.randomUUID();

        producer.enqueue(crmOrderId, "SHOP-001", shopOrderUuid,
            "SHIPPED", "DELIVERED", "Кузнецов Д.", null);

        var captor = ArgumentCaptor.forClass(KafkaOutbox.class);
        verify(outboxRepository).save(captor.capture());

        ObjectMapper mapper = new ObjectMapper();
        OrderStatusChangedEvent event = mapper.readValue(
            captor.getValue().getPayload(), OrderStatusChangedEvent.class
        );

        assertThat(event.getCrmOrderId()).isEqualTo(crmOrderId);
        assertThat(event.getShopOrderUuid()).isEqualTo(shopOrderUuid);
        assertThat(event.getNewStatus()).isEqualTo("DELIVERED");
        assertThat(event.getPreviousStatus()).isEqualTo("SHIPPED");
        assertThat(event.getChangedBy()).isEqualTo("Кузнецов Д.");
        assertThat(event.getComment()).isNull(); // NON_NULL — не должен быть в JSON
    }
}
