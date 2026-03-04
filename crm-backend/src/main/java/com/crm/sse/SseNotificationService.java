package com.crm.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Сервис SSE-уведомлений.
 *
 * Хранит активные SseEmitter-ы в разрезе тенанта (schema).
 * Когда Kafka Consumer создаёт заказ — вызывает broadcast(),
 * и все открытые вкладки получают push-событие без перезагрузки.
 *
 * Потокобезопасность: ConcurrentHashMap + CopyOnWriteArrayList.
 * При отключении клиента emitter сам удаляется из реестра.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseNotificationService {

    /** tenantSchema → список активных emitter-ов */
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters =
        new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    // ── Подписка ──────────────────────────────────────────────────────

    /**
     * Создаёт новый SseEmitter для клиента и регистрирует его.
     * Таймаут 30 минут — nginx/браузер переподключится автоматически при разрыве.
     */
    public SseEmitter subscribe(String tenantSchema) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters.computeIfAbsent(tenantSchema, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        log.debug("SSE subscribed: tenant={} total={}", tenantSchema,
            emitters.get(tenantSchema).size());

        // Удаляем из реестра при любом завершении соединения
        Runnable cleanup = () -> remove(tenantSchema, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Начальный heartbeat — подтверждает, что соединение установлено
        sendHeartbeat(emitter);

        return emitter;
    }

    // ── Broadcast ─────────────────────────────────────────────────────

    /**
     * Отправляет SSE-событие всем подписчикам тенанта.
     *
     * @param tenantSchema  схема БД тенанта
     * @param eventType     тип события: "order.created", "order.status_changed", ...
     * @param payload       произвольный объект — будет сериализован в JSON
     */
    public void broadcast(String tenantSchema, String eventType, Object payload) {
        List<SseEmitter> tenantEmitters = emitters.get(tenantSchema);
        if (tenantEmitters == null || tenantEmitters.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("SSE serialize error for eventType={}: {}", eventType, e.getMessage());
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : tenantEmitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(eventType)
                        .data(json)
                );
            } catch (IOException e) {
                // Клиент отключился — помечаем на удаление
                dead.add(emitter);
            }
        }

        if (!dead.isEmpty()) {
            tenantEmitters.removeAll(dead);
            log.debug("SSE removed {} dead emitters for tenant={}", dead.size(), tenantSchema);
        }

        log.debug("SSE broadcast: tenant={} event={} recipients={}",
            tenantSchema, eventType, tenantEmitters.size());
    }

    // ── Утилиты ───────────────────────────────────────────────────────

    private void remove(String tenantSchema, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(tenantSchema);
        if (list != null) {
            list.remove(emitter);
            log.debug("SSE unsubscribed: tenant={} remaining={}", tenantSchema, list.size());
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
        } catch (IOException e) {
            // Клиент уже отключился — ничего страшного
        }
    }

    /** Число активных соединений для метрик / дебага */
    public int countEmitters(String tenantSchema) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(tenantSchema);
        return list != null ? list.size() : 0;
    }
}
