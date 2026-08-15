// lib/format.ts
// Vietnamese-locale formatting used across the screens. One module so a unit or a decimal
// convention is changed in one place, not in eight components.

import type { SensorType, Summary } from "@/lib/api/types"

const number = new Intl.NumberFormat("vi-VN")

/**
 * How many decimals a metric deserves. pH needs one (6.2, not 6); lux needs none (850 lux,
 * not 850.0); temperature and the percentages read best at one.
 */
const DECIMALS: Record<SensorType, number> = {
  TEMPERATURE: 0,
  AIR_HUMIDITY: 0,
  SOIL_MOISTURE: 0,
  LIGHT: 0,
  PH: 1,
}

export function formatValue(value: number | null | undefined, type: SensorType): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—"
  const dp = DECIMALS[type] ?? 1
  return number.format(Number(value.toFixed(dp)))
}

export function formatWithUnit(value: number | null | undefined, type: SensorType, unit?: string | null): string {
  if (value === null || value === undefined) return "—"
  const formatted = formatValue(value, type)
  if (!unit) return formatted
  // lux and pH read as separate words; degrees and percent sit flush against the number.
  return unit === "lux" || unit === "pH" ? `${formatted} ${unit}` : `${formatted}${unit}`
}

/** The "↑ Tăng 2°C so với hôm qua" line, or null when there is no comparison window. */
export function formatTrend(summary: Summary | null): string | null {
  if (!summary || summary.delta === null || summary.previousAverage === null) return null

  const magnitude = Math.abs(summary.delta)
  // Sub-0.1 movement is noise, not a trend; claiming "↑ Tăng 0.02°C" is false precision.
  if (magnitude < 0.1) return "Ổn định so với kỳ trước"

  const unit = summary.unit ?? ""
  const formatted = formatValue(magnitude, summary.type)
  const window = summary.range === "24H" ? "hôm qua" : "kỳ trước"

  return summary.delta > 0
    ? `↑ Tăng ${formatted}${unit === "lux" || unit === "pH" ? ` ${unit}` : unit} so với ${window}`
    : `↓ Giảm ${formatted}${unit === "lux" || unit === "pH" ? ` ${unit}` : unit} so với ${window}`
}

/** Clock time in the viewer's locale, e.g. "14:32". */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return "—"
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return "—"
  return date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })
}

/** Relative wording for the timeline: "14:32" today, "Hôm qua", then a date. */
export function formatEventTime(iso: string | null | undefined): string {
  if (!iso) return "—"
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return "—"

  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  if (sameDay) return formatTime(iso)

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return "Hôm qua"

  return date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })
}

/** "Đo lần cuối: Hôm nay, 08:30" on the soil card. */
export function formatMeasuredAt(iso: string | null | undefined): string {
  if (!iso) return "Chưa có dữ liệu"
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return "Chưa có dữ liệu"

  const sameDay = date.toDateString() === new Date().toDateString()
  return sameDay
    ? `Hôm nay, ${formatTime(iso)}`
    : `${date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })}, ${formatTime(iso)}`
}

/** Axis labels for a chart, chosen from the bucket width so they do not collide. */
export function formatAxisLabel(iso: string, bucketMinutes: number): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ""
  if (bucketMinutes <= 60) return formatTime(iso)
  if (bucketMinutes <= 180) {
    return date.toLocaleDateString("vi-VN", { weekday: "short" }).replace("Thứ ", "T")
  }
  return date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })
}
