"use client"

// useDashboard — the home screen's data.
//
// One HTTP fetch for the initial paint, then a WebSocket keeps it live. Readings, actuator
// states, alerts and timeline entries all arrive as pushes and are merged into the SAME
// object the screen already renders, so there is no polling and no second source of truth.

import { useCallback, useEffect, useState } from "react"
import { getDashboard, setSystemEnabled as setSystemEnabledApi } from "@/lib/api/garden/gardenApi"
import { subscribeToGarden } from "@/lib/api/realtime"
import type { Dashboard } from "@/lib/api/types"

export interface UseDashboard {
  dashboard: Dashboard | null
  isLoading: boolean
  error: unknown
  /** WebSocket state — drives the "Live" dot in the hero. */
  isLive: boolean
  refetch: () => void
  toggleSystem: (enabled: boolean) => Promise<void>
}

export function useDashboard(gardenId: string | null): UseDashboard {
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [isLive, setIsLive] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!gardenId) {
      setIsLoading(false)
      return
    }

    let alive = true
    setIsLoading(true)
    setError(null)

    getDashboard(gardenId)
      .then((data) => {
        if (alive) setDashboard(data)
      })
      .catch((e) => {
        if (alive) setError(e)
      })
      .finally(() => {
        if (alive) setIsLoading(false)
      })

    return () => {
      alive = false
    }
  }, [gardenId, reloadKey])

  useEffect(() => {
    if (!gardenId) return

    return subscribeToGarden(gardenId, {
      onConnectionChange: setIsLive,

      onReading: (push) => {
        setDashboard((current) => {
          if (!current) return current
          return {
            ...current,
            sensors: current.sensors.map((tile) =>
              tile.sensorId === push.sensorId
                ? { ...tile, value: push.value, lastReadingAt: push.timestamp, breached: push.breached, status: "ONLINE" }
                : tile
            ),
          }
        })
      },

      onActuator: (actuator) => {
        setDashboard((current) => {
          if (!current) return current
          return {
            ...current,
            actuators: current.actuators.map((a) => (a.id === actuator.id ? actuator : a)),
          }
        })
      },

      onAlert: (_alert, unreadCount) => {
        setDashboard((current) => (current ? { ...current, unreadAlerts: unreadCount } : current))
      },

      onEvent: (event) => {
        setDashboard((current) => {
          if (!current) return current
          // Cap the in-memory timeline: a busy garden would otherwise grow this array all
          // day and re-render an ever-longer list on every push.
          return { ...current, recentEvents: [event, ...current.recentEvents].slice(0, 20) }
        })
      },
    })
  }, [gardenId])

  const toggleSystem = useCallback(
    async (enabled: boolean) => {
      if (!gardenId) return
      // Optimistic: the toggle is the emergency stop, and a spinner on it feels broken.
      setDashboard((current) =>
        current ? { ...current, garden: { ...current.garden, systemEnabled: enabled } } : current
      )
      try {
        const garden = await setSystemEnabledApi(gardenId, enabled)
        setDashboard((current) => (current ? { ...current, garden } : current))
      } catch (e) {
        // Roll back so the switch never lies about the state of the hardware.
        setDashboard((current) =>
          current ? { ...current, garden: { ...current.garden, systemEnabled: !enabled } } : current
        )
        throw e
      }
    },
    [gardenId]
  )

  const refetch = useCallback(() => setReloadKey((k) => k + 1), [])

  return { dashboard, isLoading, error, isLive, refetch, toggleSystem }
}
