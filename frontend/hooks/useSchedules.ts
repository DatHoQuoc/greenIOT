"use client"

// useSchedules — the "Lịch tưới" tab.

import { useCallback, useEffect, useState } from "react"
import { getSchedules, runScheduleNow } from "@/lib/api/schedule/scheduleApi"
import { getActuators } from "@/lib/api/actuator/actuatorApi"
import type { Actuator, IrrigationSchedule } from "@/lib/api/types"

export interface UseSchedules {
  schedules: IrrigationSchedule[]
  actuators: Actuator[]
  isLoading: boolean
  error: unknown
  /** The schedule currently being run by hand, so only its button shows a spinner. */
  runningId: string | null
  runNow: (scheduleId: string) => Promise<void>
  refetch: () => void
}

export function useSchedules(gardenId: string | null): UseSchedules {
  const [schedules, setSchedules] = useState<IrrigationSchedule[]>([])
  const [actuators, setActuators] = useState<Actuator[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [runningId, setRunningId] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!gardenId) {
      setIsLoading(false)
      return
    }

    let alive = true
    setIsLoading(true)
    setError(null)

    // Actuators come along because a schedule stores only actuatorId; the screen needs the
    // pump's NAME, and resolving that per row would be N requests.
    Promise.all([getSchedules(gardenId), getActuators(gardenId)])
      .then(([scheduleData, actuatorData]) => {
        if (!alive) return
        setSchedules(scheduleData)
        setActuators(actuatorData)
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

  const runNow = useCallback(
    async (scheduleId: string) => {
      if (!gardenId) return
      setRunningId(scheduleId)
      try {
        const updated = await runScheduleNow(gardenId, scheduleId)
        setSchedules((current) => current.map((s) => (s.id === scheduleId ? updated : s)))
      } finally {
        setRunningId(null)
      }
    },
    [gardenId]
  )

  const refetch = useCallback(() => setReloadKey((k) => k + 1), [])

  return { schedules, actuators, isLoading, error, runningId, runNow, refetch }
}
