// lib/api/realtime.ts
// STOMP-over-WebSocket subscriptions for one garden.
//
// The socket is server-push only: the app never sends over it. Commands go by HTTP, which
// keeps every write on one auditable path with one auth mechanism, and means a dropped
// socket degrades the UI to "stale until refetch" rather than breaking control.

import { Client, type IMessage } from "@stomp/stompjs"
import type { Actuator, Alert, AutomationEvent, SensorSlug, SensorType } from "@/lib/api/types"

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"

export interface ReadingPush {
  sensorId: string
  type: SensorType
  slug: SensorSlug
  value: number
  unit: string | null
  timestamp: string
  breached: boolean
}

export interface RealtimeHandlers {
  onReading?: (push: ReadingPush) => void
  onActuator?: (actuator: Actuator) => void
  onAlert?: (alert: Alert, unreadCount: number) => void
  onEvent?: (event: AutomationEvent) => void
  onConnectionChange?: (connected: boolean) => void
}

/**
 * Opens one connection carrying all four topics for a garden.
 *
 * @returns a disposer — call it on unmount. Leaking a client keeps a socket AND a
 *          reconnect timer alive, so navigating between sensor screens a few times would
 *          otherwise leave a handful of sockets fighting over the same handlers.
 */
export function subscribeToGarden(gardenId: string, handlers: RealtimeHandlers): () => void {
  const wsUrl = API_BASE.replace(/^http/, "ws") + "/ws"

  const client = new Client({
    brokerURL: wsUrl,
    // 5s is a compromise: fast enough that a phone waking from sleep reconnects before
    // the user notices, slow enough not to hammer a backend that is actually down.
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  })

  const parse = <T,>(message: IMessage): T | null => {
    try {
      return JSON.parse(message.body) as T
    } catch {
      return null
    }
  }

  client.onConnect = () => {
    handlers.onConnectionChange?.(true)
    const root = `/topic/garden/${gardenId}`

    if (handlers.onReading) {
      client.subscribe(`${root}/reading`, (m) => {
        const push = parse<ReadingPush>(m)
        if (push) handlers.onReading!(push)
      })
    }
    if (handlers.onActuator) {
      client.subscribe(`${root}/actuator`, (m) => {
        const push = parse<{ actuator: Actuator }>(m)
        if (push?.actuator) handlers.onActuator!(push.actuator)
      })
    }
    if (handlers.onAlert) {
      client.subscribe(`${root}/alert`, (m) => {
        const push = parse<{ alert: Alert; unreadCount: number }>(m)
        if (push?.alert) handlers.onAlert!(push.alert, push.unreadCount)
      })
    }
    if (handlers.onEvent) {
      client.subscribe(`${root}/event`, (m) => {
        const push = parse<{ event: AutomationEvent }>(m)
        if (push?.event) handlers.onEvent!(push.event)
      })
    }
  }

  client.onWebSocketClose = () => handlers.onConnectionChange?.(false)
  // A STOMP-level error is not fatal to the app — the data is still reachable by refetch.
  client.onStompError = (frame) => console.warn("STOMP error:", frame.headers.message)

  client.activate()

  return () => {
    handlers.onConnectionChange?.(false)
    void client.deactivate()
  }
}
