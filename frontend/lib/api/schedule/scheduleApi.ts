// lib/api/schedule/scheduleApi.ts
// The "Lịch tưới" tab.

import { client } from "@/lib/api/client"
import type { DayCode, IrrigationSchedule } from "@/lib/api/types"

export function getSchedules(gardenId: string): Promise<IrrigationSchedule[]> {
  return client.get<IrrigationSchedule[]>(`/api/v1/gardens/${gardenId}/schedules`)
}

export interface SaveSchedulePayload {
  name: string
  actuatorId: string
  enabled?: boolean
  daysOfWeek: DayCode[]
  /** "HH:mm:ss" in the garden's timezone, not the browser's. */
  startTime: string
  durationMinutes: number
  skipIfSoilMoistureAbove?: number | null
  skipIfRainForecast?: boolean
}

/** Owner-only — a schedule is configuration. Running one is operation, and that is open. */
export function createSchedule(gardenId: string, payload: SaveSchedulePayload): Promise<IrrigationSchedule> {
  return client.post<IrrigationSchedule>(`/api/v1/gardens/${gardenId}/schedules`, payload)
}

export function updateSchedule(
  gardenId: string,
  scheduleId: string,
  payload: SaveSchedulePayload
): Promise<IrrigationSchedule> {
  return client.put<IrrigationSchedule>(`/api/v1/gardens/${gardenId}/schedules/${scheduleId}`, payload)
}

export function deleteSchedule(gardenId: string, scheduleId: string): Promise<void> {
  return client.delete<void>(`/api/v1/gardens/${gardenId}/schedules/${scheduleId}`)
}

/**
 * "Tưới ngay" — overrides BOTH the master switch and the wet-soil skip.
 *
 * Those guards exist to stop the machine watering pointlessly, not to argue with a person
 * who has decided the plot needs water.
 */
export function runScheduleNow(gardenId: string, scheduleId: string): Promise<IrrigationSchedule> {
  return client.post<IrrigationSchedule>(`/api/v1/gardens/${gardenId}/schedules/${scheduleId}/run-now`)
}
