"use client"

// useAlerts — the "Cảnh báo" screen.
//
// A newly raised alert arrives over the WebSocket and is prepended to the list rather than
// triggering a refetch: the user is often looking at the screen precisely because
// something is going wrong, and reloading under them loses their scroll position.

import { useCallback, useEffect, useState } from "react"
import {
  acknowledge as acknowledgeApi,
  getAlerts,
  markAllRead as markAllReadApi,
  markRead as markReadApi,
  type AlertFilters,
} from "@/lib/api/alert/alertApi"
import { subscribeToGarden } from "@/lib/api/realtime"
import type { Alert } from "@/lib/api/types"

export interface UseAlerts {
  alerts: Alert[]
  unreadCount: number
  total: number
  page: number
  totalPages: number
  setPage: (page: number) => void
  filters: AlertFilters
  setFilters: (filters: AlertFilters) => void
  isLoading: boolean
  /** Paging: dim the list, do not flash the whole skeleton. */
  isPaging: boolean
  error: unknown
  markRead: (alertId: string) => Promise<void>
  acknowledge: (alertId: string) => Promise<void>
  markAllRead: () => Promise<void>
  refetch: () => void
}

export function useAlerts(gardenId: string | null): UseAlerts {
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [filters, setFiltersState] = useState<AlertFilters>({})
  const [isLoading, setIsLoading] = useState(true)
  const [isPaging, setIsPaging] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [firstLoadDone, setFirstLoadDone] = useState(false)

  useEffect(() => {
    if (!gardenId) {
      setIsLoading(false)
      return
    }

    let alive = true
    if (firstLoadDone) setIsPaging(true)
    else setIsLoading(true)
    setError(null)

    getAlerts(gardenId, filters, page)
      .then((data) => {
        if (!alive) return
        setAlerts(data.items)
        setTotal(data.totalItems)
        setTotalPages(data.totalPages)
        setUnreadCount(data.items.filter((a) => !a.read).length)
      })
      .catch((e) => {
        if (alive) setError(e)
      })
      .finally(() => {
        if (!alive) return
        setIsLoading(false)
        setIsPaging(false)
        setFirstLoadDone(true)
      })

    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gardenId, page, filters, reloadKey])

  useEffect(() => {
    if (!gardenId) return
    return subscribeToGarden(gardenId, {
      onAlert: (alert, unread) => {
        setUnreadCount(unread)
        // Only the first page shows the newest alert; prepending on page 3 would put a row
        // on screen that does not belong to the window the user is reading.
        if (page === 0) {
          setAlerts((current) => [alert, ...current.filter((a) => a.id !== alert.id)].slice(0, 20))
        }
      },
    })
  }, [gardenId, page])

  const setFilters = useCallback((next: AlertFilters) => {
    setFiltersState(next)
    setPage(0) // a filtered list has different pages; staying on page 3 would show nothing
  }, [])

  const patchLocal = (alertId: string, patch: Partial<Alert>) =>
    setAlerts((current) => current.map((a) => (a.id === alertId ? { ...a, ...patch } : a)))

  const markRead = useCallback(
    async (alertId: string) => {
      if (!gardenId) return
      patchLocal(alertId, { read: true })
      setUnreadCount((c) => Math.max(0, c - 1))
      const updated = await markReadApi(gardenId, alertId)
      patchLocal(alertId, updated)
    },
    [gardenId]
  )

  const acknowledge = useCallback(
    async (alertId: string) => {
      if (!gardenId) return
      const updated = await acknowledgeApi(gardenId, alertId)
      patchLocal(alertId, updated)
    },
    [gardenId]
  )

  const markAllRead = useCallback(async () => {
    if (!gardenId) return
    setAlerts((current) => current.map((a) => ({ ...a, read: true })))
    setUnreadCount(0)
    await markAllReadApi(gardenId)
  }, [gardenId])

  const refetch = useCallback(() => setReloadKey((k) => k + 1), [])

  return {
    alerts,
    unreadCount,
    total,
    page,
    totalPages,
    setPage,
    filters,
    setFilters,
    isLoading,
    isPaging,
    error,
    markRead,
    acknowledge,
    markAllRead,
    refetch,
  }
}
