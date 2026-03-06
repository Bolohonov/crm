/**
 * useServerEvents — composable для SSE-подписки на push-уведомления от сервера.
 *
 * Использование:
 *   const { on, off } = useServerEvents()
 *   on('order.created', (event) => { ... })
 *
 * Особенности:
 *   - Соединение открывается один раз при первом вызове useServerEvents()
 *     и живёт всё время пока приложение открыто (singleton-паттерн)
 *   - Автоматическое переподключение при разрыве (встроено в EventSource)
 *   - JWT-токен передаётся через query-параметр (EventSource не поддерживает заголовки)
 *   - При logout соединение закрывается через disconnect()
 */

import { onUnmounted } from 'vue'

// ── Типы событий ─────────────────────────────────────────────────────

export interface SseOrderCreatedEvent {
  type: 'order.created'
  orderId: string
  externalOrderId?: string
  customerName?: string
  totalAmount?: number
  occurredAt: string
}

export interface SseOrderStatusChangedEvent {
  type: 'order.status_changed'
  orderId: string
  externalOrderId?: string
  previousStatus: string
  newStatus: string
  occurredAt: string
}

export type SseEvent = SseOrderCreatedEvent | SseOrderStatusChangedEvent

type EventHandler<T = SseEvent> = (event: T) => void
type EventType = 'order.created' | 'order.status_changed' | 'heartbeat'

// ── Singleton-состояние ───────────────────────────────────────────────

let eventSource: EventSource | null = null
const listeners = new Map<EventType, Set<EventHandler>>()
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let isConnecting = false

// ── Управление соединением ────────────────────────────────────────────

function connect() {
  if (eventSource?.readyState === EventSource.OPEN || isConnecting) return

  const token = localStorage.getItem('accessToken')
  if (!token) return   // не авторизован — не подключаемся

  isConnecting = true

  // EventSource не поддерживает Authorization header — передаём токен в query
  const apiBase = import.meta.env.VITE_API_BASE_URL || '/crm/api/v1'
  const url = `${apiBase}/events/subscribe?token=${encodeURIComponent(token)}`
  eventSource = new EventSource(url)

  eventSource.onopen = () => {
    isConnecting = false
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
    console.debug('[SSE] Connected')
  }

  eventSource.onerror = () => {
    isConnecting = false
    // EventSource сам переподключается, но если токен истёк — нужно обновить URL
    // Даём 5 секунд и переоткрываем с новым токеном
    if (!reconnectTimer) {
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        eventSource?.close()
        eventSource = null
        connect()
      }, 5000)
    }
  }

  // Вешаем обработчики на все известные типы событий
  const knownEvents: EventType[] = ['order.created', 'order.status_changed', 'heartbeat']
  knownEvents.forEach(eventType => {
    eventSource!.addEventListener(eventType, (e: MessageEvent) => {
      dispatch(eventType, e.data)
    })
  })
}

function disconnect() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  eventSource?.close()
  eventSource = null
  listeners.clear()
  isConnecting = false
  console.debug('[SSE] Disconnected')
}

function dispatch(eventType: EventType, rawData: string) {
  const handlers = listeners.get(eventType)
  if (!handlers || handlers.size === 0) return

  try {
    const data = eventType === 'heartbeat' ? rawData : JSON.parse(rawData)
    handlers.forEach(h => {
      try { h(data) } catch (e) { console.error('[SSE] Handler error:', e) }
    })
  } catch (e) {
    console.error('[SSE] Parse error for event', eventType, e)
  }
}

// ── Public API ────────────────────────────────────────────────────────

export function useServerEvents() {
  // Подключаемся при первом использовании
  connect()

  function on<T extends EventType>(
      eventType: T,
      handler: EventHandler
  ): void {
    if (!listeners.has(eventType)) {
      listeners.set(eventType, new Set())
    }
    listeners.get(eventType)!.add(handler)
  }

  function off<T extends EventType>(
      eventType: T,
      handler: EventHandler
  ): void {
    listeners.get(eventType)?.delete(handler)
  }

  /**
   * Удобная обёртка: подписывается и автоматически отписывается
   * при размонтировании компонента.
   */
  function onEvent<T extends EventType>(
      eventType: T,
      handler: EventHandler
  ): void {
    on(eventType, handler)
    onUnmounted(() => off(eventType, handler))
  }

  return { on, off, onEvent, disconnect }
}

// ── JWT refresh hook — вызывать после обновления токена ──────────────

/**
 * Переподключает SSE с обновлённым токеном.
 * Вызывать из auth interceptor после успешного refresh.
 */
export function reconnectSse() {
  eventSource?.close()
  eventSource = null
  isConnecting = false
  connect()
}
