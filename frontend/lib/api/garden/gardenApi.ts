// lib/api/garden/gardenApi.ts
// One place for /api/v1/gardens/** — the dashboard, the master switch, thresholds,
// membership and the automation timeline.
//
// BE serialises camelCase and returns the ApiResponse envelope; client.ts unwraps the
// envelope, so these functions resolve to the payload itself. No field mapping happens
// here on purpose — a translation layer between two camelCase sides is pure overhead and
// one more place for a name to drift.

import { client } from "@/lib/api/client"
import type { AutomationEvent, Dashboard, Garden, SensorType, Threshold } from "@/lib/api/types"

export function getGardens(): Promise<Garden[]> {
  return client.get<Garden[]>("/api/v1/gardens")
}

export function getGarden(gardenId: string): Promise<Garden> {
  return client.get<Garden>(`/api/v1/gardens/${gardenId}`)
}

/**
 * Everything the home screen renders, in one request.
 *
 * Deliberately not five parallel calls: the phone paints the whole screen at once, and
 * five round trips on a mobile connection is five chances to show a half-filled layout.
 */
export function getDashboard(gardenId: string): Promise<Dashboard> {
  return client.get<Dashboard>(`/api/v1/gardens/${gardenId}/dashboard`)
}

export interface CreateGardenPayload {
  name: string
  description?: string
  type?: string
  areaSqm?: number
  timezone?: string
  plantProfileId?: string
  location?: { latitude?: number; longitude?: number; address?: string }
}

export function createGarden(payload: CreateGardenPayload): Promise<Garden> {
  return client.post<Garden>("/api/v1/gardens", payload)
}

/**
 * The hero toggle — suppresses every rule and schedule.
 *
 * Open to household members, not just the owner: it is the emergency stop, and someone
 * standing in a flooding garden should not have to find the owner first.
 */
export function setSystemEnabled(gardenId: string, enabled: boolean): Promise<Garden> {
  return client.patch<Garden>(`/api/v1/gardens/${gardenId}/system`, { enabled })
}

/** Owner-only; the UI should hide the control when `garden.viewerIsOwner` is false. */
export function updateThresholds(
  gardenId: string,
  thresholds: Partial<Record<SensorType, Threshold>>
): Promise<Garden> {
  return client.put<Garden>(`/api/v1/gardens/${gardenId}/thresholds`, { thresholds })
}

/** "Lịch sử kích hoạt tự động". Pass sensorId to scope it to one metric's detail screen. */
export function getEvents(gardenId: string, limit = 20, sensorId?: string): Promise<AutomationEvent[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  if (sensorId) q.set("sensorId", sensorId)
  return client.get<AutomationEvent[]>(`/api/v1/gardens/${gardenId}/events?${q.toString()}`)
}

// ── Membership (owner-only) ──────────────────────────────────────────────────
// The invitee must already have an account — BE answers 404 otherwise rather than
// creating one from an email address.

export function addMember(gardenId: string, email: string): Promise<Garden> {
  return client.post<Garden>(`/api/v1/gardens/${gardenId}/members`, { email })
}

export function removeMember(gardenId: string, memberUserId: string): Promise<Garden> {
  return client.delete<Garden>(`/api/v1/gardens/${gardenId}/members/${memberUserId}`)
}
