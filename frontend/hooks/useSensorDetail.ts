"use client"

// useSensorDetail — the per-metric screen: hero stats, chart series, and the timeline
// scoped to that sensor.
//
// Summary and series are separate calls on purpose. The summary's min/max/delta are
// computed over the FULL window server-side; deriving them from the chart points instead
// would quietly give different numbers, because those points are bucketed averages.

import { useCallback, useEffect, useState } from "react"
import { getSeries, getSummary } from "@/lib/api/sensor/sensorApi"
import { getEvents } from "@/lib/api/garden/gardenApi"
import { subscribeToGarden } from "@/lib/api/realtime"
import type { AutomationEvent, RangeKey, SensorSlug, Series, Summary } from "@/lib/api/types"

export interface UseSensorDetail {
  summary: Summary | null
  series: Series | null
  events: AutomationEvent[]
  isLoading: boolean
  /** True while switching range — dim the chart instead of flashing the whole skeleton. */
  isRangeChanging: boolean
  error: unknown
  range: RangeKey
  setRange: (range: RangeKey) => void
  refetch: () => void
}

export function useSensorDetail(gardenId: string | null, slug: SensorSlug): UseSensorDetail {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [series, setSeries] = useState<Series | null>(null)
  const [events, setEvents] = useState<AutomationEvent[]>([])
  const [range, setRange] = useState<RangeKey>("24H")
  const [isLoading, setIsLoading] = useState(true)
  const [isRangeChanging, setIsRangeChanging] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [firstLoadDone, setFirstLoadDone] = useState(false)

  useEffect(() => {
    if (!gardenId) {
      setIsLoading(false)
      return
    }

    let alive = true
    if (firstLoadDone) setIsRangeChanging(true)
    else setIsLoading(true)
    setError(null)

    Promise.all([getSummary(gardenId, slug, range), getSeries(gardenId, slug, range)])
      .then(([summaryData, seriesData]) => {
        if (!alive) return
        setSummary(summaryData)
        setSeries(seriesData)
      })
      .catch((e) => {
        if (alive) setError(e)
      })
      .finally(() => {
        if (!alive) return
        setIsLoading(false)
        setIsRangeChanging(false)
        setFirstLoadDone(true)
      })

    return () => {
      alive = false
    }
    // firstLoadDone is intentionally not a dependency: it only picks which spinner to show.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gardenId, slug, range, reloadKey])

  // The timeline does not depend on the selected range, so changing range must not refetch
  // it — that would be a wasted request on every tab press.
  useEffect(() => {
    if (!gardenId) return
    let alive = true

    getEvents(gardenId, 10)
      .then((data) => {
        if (alive) setEvents(data)
      })
      .catch(() => {
        // The timeline is supporting detail; failing to load it must not blank the screen.
      })

    return () => {
      alive = false
    }
  }, [gardenId, reloadKey])

  // Live values update the hero card without redrawing the chart: a bucketed series does
  // not change meaningfully from one sample, but the "hiện tại" number should.
  useEffect(() => {
    if (!gardenId) return

    return subscribeToGarden(gardenId, {
      onReading: (push) => {
        if (push.slug !== slug) return
        setSummary((current) => (current ? { ...current, current: push.value } : current))
      },
      onEvent: (event) => {
        setEvents((current) => [event, ...current].slice(0, 10))
      },
    })
  }, [gardenId, slug])

  const refetch = useCallback(() => setReloadKey((k) => k + 1), [])

  return { summary, series, events, isLoading, isRangeChanging, error, range, setRange, refetch }
}
