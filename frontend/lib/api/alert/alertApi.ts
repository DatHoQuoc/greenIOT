// lib/api/alert/alertApi.ts
// The "Cảnh báo" tab and the bell badge.

import { client } from "@/lib/api/client"
import type { Alert, AlertStatus, Page } from "@/lib/api/types"

export const ALERT_PAGE_SIZE = 20

export interface AlertFilters {
  status?: AlertStatus
  unreadOnly?: boolean
}

export function getAlerts(
  gardenId: string,
  filters: AlertFilters = {},
  page = 0,
  size = ALERT_PAGE_SIZE
): Promise<Page<Alert>> {
  const q = new URLSearchParams({ page: String(page), size: String(size) })
  if (filters.status) q.set("status", filters.status)
  if (filters.unreadOnly) q.set("unreadOnly", "true")
  return client.get<Page<Alert>>(`/api/v1/gardens/${gardenId}/alerts?${q.toString()}`)
}

/**
 * Badge count only. Separate from getAlerts because the badge appears on screens that
 * never render the list, and pulling 20 alerts to count them would be wasteful.
 */
export async function getUnreadCount(gardenId: string): Promise<number> {
  const res = await client.get<{ unread: number }>(`/api/v1/gardens/${gardenId}/alerts/unread-count`)
  return res.unread
}

export function markRead(gardenId: string, alertId: string): Promise<Alert> {
  return client.patch<Alert>(`/api/v1/gardens/${gardenId}/alerts/${alertId}/read`, {})
}

/** "I have seen this and I am handling it" — stronger than read, and it stops re-notifying. */
export function acknowledge(gardenId: string, alertId: string): Promise<Alert> {
  return client.patch<Alert>(`/api/v1/gardens/${gardenId}/alerts/${alertId}/acknowledge`, {})
}

export function markAllRead(gardenId: string): Promise<number> {
  return client.post<number>(`/api/v1/gardens/${gardenId}/alerts/read-all`)
}
