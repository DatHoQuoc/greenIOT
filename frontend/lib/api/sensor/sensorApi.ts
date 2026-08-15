// lib/api/sensor/sensorApi.ts
// Readings, charts and the sensor registry.
//
// The app routes by slug (/sensor/soil-moisture) while BE speaks enums; the slug goes
// straight onto the query string because BE accepts either form (SensorType.fromSlug).

import { client } from "@/lib/api/client"
import type { RangeKey, Sensor, SensorSlug, Series, Summary } from "@/lib/api/types"

export function getSensors(gardenId: string): Promise<Sensor[]> {
  return client.get<Sensor[]>(`/api/v1/gardens/${gardenId}/sensors`)
}

/**
 * Down-sampled chart data. BE picks the bucket width from the range (24H→60m, 7D→3h,
 * 30D→12h) so a chart stays a few dozen points no matter how fast the probes publish —
 * a raw 30-day pull at 60s sampling would be 43 200 points.
 */
export function getSeries(gardenId: string, slug: SensorSlug, range: RangeKey): Promise<Series> {
  return client.get<Series>(`/api/v1/gardens/${gardenId}/readings/series?type=${slug}&range=${range}`)
}

/** The "Tùy chỉnh" range: BE sizes the bucket from the span it is given. */
export function getSeriesBetween(
  gardenId: string,
  slug: SensorSlug,
  from: Date,
  to: Date
): Promise<Series> {
  const q = new URLSearchParams({
    type: slug,
    from: from.toISOString(),
    to: to.toISOString(),
  })
  return client.get<Series>(`/api/v1/gardens/${gardenId}/readings/series?${q.toString()}`)
}

/**
 * Current / min / max plus the delta against the preceding window — i.e. everything on
 * the hero card including "↑ Tăng 2°C so với hôm qua".
 *
 * The delta is computed server-side against the full window. The FE must not recompute it
 * from the chart points: those are bucketed averages, so the answer would quietly differ.
 */
export function getSummary(gardenId: string, slug: SensorSlug, range: RangeKey): Promise<Summary> {
  return client.get<Summary>(`/api/v1/gardens/${gardenId}/readings/summary?type=${slug}&range=${range}`)
}

/**
 * Tải dữ liệu cảm biến về máy.
 *
 * Không dùng `<a href>` được: token là Bearer trong bộ nhớ, mà điều hướng trình duyệt
 * không mang header Authorization → 401 và người dùng nhận một file lỗi. Nên fetch có
 * token rồi dựng blob URL.
 *
 * Tên file do FE đặt: `Content-Disposition` của BE không đọc được từ JS khi thiếu
 * `Access-Control-Expose-Headers`, và thêm header CORS chỉ để lấy cái tên là đắt hơn
 * việc dựng lại đúng cái tên đó ở đây.
 */
export async function downloadReadings(
  gardenId: string,
  slug: SensorSlug,
  format: "csv" | "json" = "csv"
): Promise<void> {
  const blob = await client.getBlob(
    `/api/v1/gardens/${gardenId}/export/readings.${format}?type=${slug}`
  )
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = `greensense-${slug}.${format}`
  document.body.appendChild(a)
  a.click()
  a.remove()
  // Thu hồi ngay sau khi click là quá sớm ở Safari (nó đọc blob bất đồng bộ) — hoãn một nhịp.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
