'use client'

// One screen for every metric.
//
// There used to be four near-identical copies of this file (air-humidity, light,
// soil-moisture and this one). Three of them still said "Nhiệt độ theo thời gian" and
// "28°C" in the chart header because the copy-paste was never finished — which is exactly
// the failure a single parameterised route cannot have.
//
// pH keeps its own route: /sensor/ph is the soil-analysis screen, a genuinely different
// layout, and Next.js gives a static segment precedence over this dynamic one.

import Link from 'next/link'
import { useParams, useRouter } from 'next/navigation'
import { useEffect } from 'react'
import {
  ArrowDownToLine,
  ArrowLeft,
  ChevronDown,
  Droplets,
  Fan,
  FlaskConical,
  SunMedium,
  ThermometerSun,
  Waves,
  Wind,
} from 'lucide-react'

import { useSession } from '@/hooks/useSession'
import { useSensorDetail } from '@/hooks/useSensorDetail'
import { ErrorState, Skeleton } from '@/components/screen-state'
import { SensorChart } from '@/components/sensor-chart'
import { downloadReadings } from '@/lib/api/sensor/sensorApi'
import { formatEventTime, formatTrend, formatWithUnit } from '@/lib/format'
import { isSensorSlug, type RangeKey, type SensorSlug, type SensorType } from '@/lib/api/types'

const TABS: { slug: SensorSlug; label: string; icon: typeof ThermometerSun }[] = [
  { slug: 'temperature', label: 'Nhiệt độ', icon: ThermometerSun },
  { slug: 'air-humidity', label: 'Độ ẩm KK', icon: Droplets },
  { slug: 'soil-moisture', label: 'Độ ẩm đất', icon: Waves },
  { slug: 'light', label: 'Ánh sáng', icon: SunMedium },
  { slug: 'ph', label: 'pH', icon: FlaskConical },
]

const HERO_TONE: Record<SensorSlug, string> = {
  temperature: '',
  'air-humidity': 'sensor-hero-teal',
  'soil-moisture': 'sensor-hero-orange',
  light: 'sensor-hero-gold',
  ph: '',
}

const RANGES: { key: RangeKey; label: string }[] = [
  { key: '24H', label: '24H' },
  { key: '7D', label: '7 Ngày' },
  { key: '30D', label: '30 Ngày' },
]

const EVENT_TONE_CLASS: Record<string, string> = {
  GREEN: 'green',
  TEAL: 'teal',
  GRAY: 'gray',
  AMBER: 'amber',
  RED: 'red',
}

export default function SensorDetailPage() {
  const params = useParams<{ id: string }>()
  const router = useRouter()
  const { gardenId, isBootstrapping, isAuthenticated } = useSession()

  const raw = params?.id ?? 'temperature'
  const slug: SensorSlug = isSensorSlug(raw) ? raw : 'temperature'

  const { summary, series, events, isLoading, isRangeChanging, error, range, setRange, refetch } =
    useSensorDetail(gardenId, slug)

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) router.replace('/login')
  }, [isBootstrapping, isAuthenticated, router])

  // An unknown slug is a bad URL, not an empty garden — send the user somewhere real.
  useEffect(() => {
    if (!isSensorSlug(raw)) router.replace('/sensor/temperature')
  }, [raw, router])

  const tab = TABS.find((t) => t.slug === slug)!
  const HeroIcon = tab.icon
  const type: SensorType = summary?.type ?? 'TEMPERATURE'
  const trend = formatTrend(summary)

  return (
    <main className="sensor-detail-shell">
      <div className="sensor-detail-content">
        <header className="sensor-detail-header">
          <Link href="/" className="sensor-back" aria-label="Quay lại trang chủ">
            <ArrowLeft size={20} />
          </Link>
          <h1>Chi tiết cảm biến</h1>
          <button className="sensor-filter" aria-label="Chọn khoảng thời gian">
            <ChevronDown size={18} />
          </button>
        </header>

        <nav className="sensor-selector" role="tablist" aria-label="Loại cảm biến">
          {TABS.map(({ slug: tabSlug, label, icon: Icon }) => (
            <Link
              key={tabSlug}
              href={`/sensor/${tabSlug}`}
              role="tab"
              aria-selected={tabSlug === slug}
              className={tabSlug === slug ? 'active' : ''}
            >
              <Icon size={14} />
              {label}
            </Link>
          ))}
        </nav>

        {isLoading ? (
          <div className="gs-skeleton-stack">
            <Skeleton height={140} radius={20} />
            <Skeleton height={36} radius={14} />
            <Skeleton height={190} radius={18} />
          </div>
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : (
          <>
            <section className={`sensor-hero ${HERO_TONE[slug]}`} aria-label={`${tab.label} hiện tại`}>
              <div className="sensor-hero-label">
                <HeroIcon size={19} />
                <span>{tab.label} hiện tại</span>
              </div>
              <strong>{formatWithUnit(summary?.current, type, summary?.unit)}</strong>
              {trend && (
                <p className={`sensor-trend${summary?.trend === 'down' ? ' sensor-warning' : ''}`}>{trend}</p>
              )}
              <div className="sensor-minmax">
                <span>
                  Thấp nhất: <b>{formatWithUnit(summary?.min, type, summary?.unit)}</b>
                </span>
                <span>
                  Cao nhất: <b>{formatWithUnit(summary?.max, type, summary?.unit)}</b>
                </span>
              </div>
            </section>

            <div className="range-selector" role="tablist" aria-label="Khoảng thời gian">
              {RANGES.map(({ key, label }) => (
                <button
                  key={key}
                  role="tab"
                  aria-selected={range === key}
                  className={range === key ? 'active' : ''}
                  onClick={() => setRange(key)}
                >
                  {label}
                </button>
              ))}
            </div>

            <article className="temperature-chart-card">
              <div className="detail-section-heading">
                <div>
                  {/* Header follows the metric now — this is the line the copied pages got wrong. */}
                  <p className="eyebrow">LỊCH SỬ {tab.label.toUpperCase()}</p>
                  <h2>{tab.label} theo thời gian</h2>
                </div>
                <span>{formatWithUnit(summary?.current, type, summary?.unit)}</span>
              </div>
              {series && (
                <SensorChart
                  series={series}
                  type={type}
                  threshold={summary?.threshold}
                  dimmed={isRangeChanging}
                  gradientId={`gs-chart-${slug}`}
                />
              )}
            </article>

            <article className="timeline-card">
              <h2>Lịch sử kích hoạt tự động</h2>
              {events.length === 0 ? (
                <p className="gs-chart-empty">Chưa có hoạt động nào được ghi nhận</p>
              ) : (
                <div className="timeline">
                  {events.map((event) => (
                    <div key={event.id}>
                      <i className={EVENT_TONE_CLASS[event.tone] ?? 'gray'} />
                      <span>
                        <b>{formatEventTime(event.occurredAt)}</b>
                        {event.title}
                        {event.detail && (
                          <>
                            <br />
                            <small>({event.detail})</small>
                          </>
                        )}
                      </span>
                      {event.category === 'ACTUATOR_CHANGE' ? <Fan size={17} /> : <Wind size={17} />}
                    </div>
                  ))}
                </div>
              )}
            </article>

            <button
              className="export-button"
              onClick={() => gardenId && downloadReadings(gardenId, slug, 'csv')}
              disabled={!gardenId}
            >
              <ArrowDownToLine size={17} /> Xuất dữ liệu
            </button>
          </>
        )}
      </div>
    </main>
  )
}
