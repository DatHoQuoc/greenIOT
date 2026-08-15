"use client"

// useSoil — the soil-analysis screen.
//
// The fertiliser plan comes from BE, derived from the pH band. The original mockup
// hardcoded "NPK 16-16-8 + Vôi bột" next to a hardcoded 6.2; keeping the derivation on the
// server is what stops the advice drifting away from the reading.

import { useCallback, useEffect, useState } from "react"
import {
  getLatestSoil,
  getPhScale,
  getSoilHistory,
  getTodayFertilizer,
  markFertilizerApplied,
  unmarkFertilizerToday,
  type PhZoneReference,
} from "@/lib/api/soil/soilApi"
import type { SoilAnalysis } from "@/lib/api/types"

export interface UseSoil {
  latest: SoilAnalysis | null
  history: SoilAnalysis[]
  phScale: PhZoneReference[]
  fertilizedToday: boolean
  isLoading: boolean
  isSaving: boolean
  error: unknown
  /** Idempotent per calendar day; calling it twice is a no-op, not a duplicate row. */
  toggleFertilizedToday: () => Promise<void>
  refetch: () => void
}

export function useSoil(gardenId: string | null): UseSoil {
  const [latest, setLatest] = useState<SoilAnalysis | null>(null)
  const [history, setHistory] = useState<SoilAnalysis[]>([])
  const [phScale, setPhScale] = useState<PhZoneReference[]>([])
  const [fertilizedToday, setFertilizedToday] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!gardenId) {
      setIsLoading(false)
      return
    }

    let alive = true
    setIsLoading(true)
    setError(null)

    Promise.all([
      getLatestSoil(gardenId),
      getSoilHistory(gardenId),
      getPhScale(gardenId),
      getTodayFertilizer(gardenId),
    ])
      .then(([latestData, historyData, scaleData, today]) => {
        if (!alive) return
        setLatest(latestData)
        setHistory(historyData)
        setPhScale(scaleData)
        setFertilizedToday(today.applied)
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

  const toggleFertilizedToday = useCallback(async () => {
    if (!gardenId || isSaving) return

    const next = !fertilizedToday
    setIsSaving(true)
    // Optimistic: this is a one-tap confirmation, and a spinner on it reads as a failure.
    setFertilizedToday(next)

    try {
      if (next) {
        await markFertilizerApplied(gardenId)
      } else {
        await unmarkFertilizerToday(gardenId)
      }
    } catch (e) {
      setFertilizedToday(!next)
      throw e
    } finally {
      setIsSaving(false)
    }
  }, [gardenId, fertilizedToday, isSaving])

  const refetch = useCallback(() => setReloadKey((k) => k + 1), [])

  return { latest, history, phScale, fertilizedToday, isLoading, isSaving, error, toggleFertilizedToday, refetch }
}
