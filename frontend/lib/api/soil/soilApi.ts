// lib/api/soil/soilApi.ts
// The soil screen: pH, the fertiliser plan derived from it, and the "đã bón phân" log.
//
// The recommendation is computed by BE from the pH band — the FE must NOT hardcode
// "NPK 16-16-8 + Vôi bột" for 6.2 the way the original mockup did, or the advice silently
// stops matching the reading.

import { client } from "@/lib/api/client"
import type { SoilAnalysis, SoilPhZone } from "@/lib/api/types"

export function getLatestSoil(gardenId: string): Promise<SoilAnalysis | null> {
  return client.get<SoilAnalysis | null>(`/api/v1/gardens/${gardenId}/soil/latest`)
}

export function getSoilHistory(gardenId: string, from?: Date, to?: Date): Promise<SoilAnalysis[]> {
  const q = new URLSearchParams()
  if (from) q.set("from", from.toISOString())
  if (to) q.set("to", to.toISOString())
  const qs = q.toString()
  return client.get<SoilAnalysis[]>(`/api/v1/gardens/${gardenId}/soil/history${qs ? `?${qs}` : ""}`)
}

/** Entering a pH measured with a test kit; returns the plan for that band. */
export function analyzeSoil(gardenId: string, ph: number): Promise<SoilAnalysis> {
  return client.post<SoilAnalysis>(`/api/v1/gardens/${gardenId}/soil/analyze`, { ph })
}

export interface PhZoneReference {
  zone: SoilPhZone
  label: string
  from: number
  to: number
  suitableFor: string
}

/** Reference bands for the "Thang đo pH đất" card. */
export function getPhScale(gardenId: string): Promise<PhZoneReference[]> {
  return client.get<PhZoneReference[]>(`/api/v1/gardens/${gardenId}/soil/ph-scale`)
}

export interface FertilizerApplication {
  id: string
  appliedOn: string
  fertilizerName: string
  dosage: string | null
  note: string | null
  soilAnalysisId: string | null
}

export interface TodayFertilizer {
  applied: boolean
  application: FertilizerApplication | null
}

/**
 * BE answers the question directly rather than handing over a date to compare, because
 * "today" is the GARDEN's calendar day in its own timezone — a browser in another zone
 * would get it wrong around midnight.
 */
export function getTodayFertilizer(gardenId: string): Promise<TodayFertilizer> {
  return client.get<TodayFertilizer>(`/api/v1/gardens/${gardenId}/soil/fertilizer/today`)
}

/** Idempotent per calendar day; a second call the same day is a 409 ALREADY_FERTILIZED. */
export function markFertilizerApplied(gardenId: string, note?: string): Promise<FertilizerApplication> {
  return client.post<FertilizerApplication>(`/api/v1/gardens/${gardenId}/soil/fertilizer`, { note })
}

export function unmarkFertilizerToday(gardenId: string): Promise<void> {
  return client.delete<void>(`/api/v1/gardens/${gardenId}/soil/fertilizer/today`)
}
