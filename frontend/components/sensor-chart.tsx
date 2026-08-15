"use client"

// SVG line chart driven by a real Series from the API.
//
// The original screens drew a hardcoded polyline string. This computes the path from the
// data, which also means the y-axis has to be derived — and the threshold line has to
// participate in that derivation, otherwise a 30 °C warning line ends up drawn outside a
// chart whose readings only reached 28 °C.

import { formatAxisLabel, formatValue } from "@/lib/format"
import type { SensorType, Series, Threshold } from "@/lib/api/types"

const WIDTH = 360
const HEIGHT = 125
const PAD_LEFT = 26
const PAD_RIGHT = 10
const PAD_TOP = 12
const PAD_BOTTOM = 18

interface Props {
  series: Series
  type: SensorType
  threshold?: Threshold | null
  /** Dims the chart during a range change instead of unmounting it (avoids layout jump). */
  dimmed?: boolean
  gradientId?: string
}

export function SensorChart({ series, type, threshold, dimmed, gradientId = "gs-chart-fill" }: Props) {
  const points = series.points

  if (points.length === 0) {
    return (
      <div className="gs-chart-empty" role="img" aria-label="Chưa có dữ liệu trong khoảng thời gian này">
        Chưa có dữ liệu trong khoảng này
      </div>
    )
  }

  const values = points.map((p) => p.value)
  const candidates = [...values]
  // Include the warning lines in the scale so they are always visible on the plot.
  if (threshold?.warnHigh != null) candidates.push(threshold.warnHigh)
  if (threshold?.warnLow != null) candidates.push(threshold.warnLow)

  let min = Math.min(...candidates)
  let max = Math.max(...candidates)
  // A perfectly flat series would divide by zero; give it a band to sit in the middle of.
  if (max - min < 1e-6) {
    min -= 1
    max += 1
  }
  const pad = (max - min) * 0.1
  min -= pad
  max += pad

  const plotWidth = WIDTH - PAD_LEFT - PAD_RIGHT
  const plotHeight = HEIGHT - PAD_TOP - PAD_BOTTOM

  const x = (index: number) =>
    PAD_LEFT + (points.length === 1 ? plotWidth / 2 : (index / (points.length - 1)) * plotWidth)
  const y = (value: number) => PAD_TOP + plotHeight - ((value - min) / (max - min)) * plotHeight

  const line = points.map((p, i) => `${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ")
  const area = `M${PAD_LEFT},${PAD_TOP + plotHeight} L${line.split(" ").join(" L")} L${x(points.length - 1)},${PAD_TOP + plotHeight} Z`

  // Four evenly spaced labels; more than that collides on a 360px-wide phone chart.
  const labelIndices = points.length <= 4
    ? points.map((_, i) => i)
    : [0, Math.floor(points.length / 3), Math.floor((points.length * 2) / 3), points.length - 1]

  return (
    <>
      <svg
        className="gs-chart"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`Biểu đồ ${series.type} trong khoảng ${series.range}`}
        style={{ opacity: dimmed ? 0.45 : 1, transition: "opacity .2s" }}
      >
        <defs>
          <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#7fb8a6" stopOpacity=".4" />
            <stop offset="100%" stopColor="#7fb8a6" stopOpacity="0" />
          </linearGradient>
        </defs>

        {threshold?.warnHigh != null && (
          <>
            <line
              x1={PAD_LEFT} x2={WIDTH - PAD_RIGHT}
              y1={y(threshold.warnHigh)} y2={y(threshold.warnHigh)}
              stroke="#c9a876" strokeDasharray="5 5"
            />
            <text x={WIDTH - PAD_RIGHT} y={y(threshold.warnHigh) - 4} fill="#b28758" fontSize="8" textAnchor="end">
              Ngưỡng {formatValue(threshold.warnHigh, type)}{series.unit}
            </text>
          </>
        )}
        {threshold?.warnLow != null && (
          <line
            x1={PAD_LEFT} x2={WIDTH - PAD_RIGHT}
            y1={y(threshold.warnLow)} y2={y(threshold.warnLow)}
            stroke="#c9a876" strokeDasharray="5 5" opacity=".6"
          />
        )}

        <path d={area} fill={`url(#${gradientId})`} />
        <polyline
          points={line}
          fill="none"
          stroke="#5d9d88"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        {/* Only the newest point gets a marker; one dot per bucket is noise at 60 points. */}
        <circle
          cx={x(points.length - 1)}
          cy={y(points[points.length - 1].value)}
          r="4.5"
          fill="#294a35"
          stroke="#fffdf8"
          strokeWidth="2"
        />

        <text x="2" y={PAD_TOP + 6} fill="#9ba59d" fontSize="8">{formatValue(max, type)}</text>
        <text x="2" y={PAD_TOP + plotHeight} fill="#9ba59d" fontSize="8">{formatValue(min, type)}</text>
      </svg>

      <div className="gs-chart-axis">
        {labelIndices.map((i) => (
          <span key={points[i].timestamp}>{formatAxisLabel(points[i].timestamp, series.bucketMinutes)}</span>
        ))}
      </div>
    </>
  )
}
