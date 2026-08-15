'use client'

// Soil analysis. Its own route (not the shared /sensor/[id] screen) because it is a
// different layout: a pH scale, a fertiliser plan, and the "đã bón phân" action.
//
// The recommendation is NOT hardcoded here any more. BE derives it from the pH band, so a
// reading of 4.9 or 8.1 gets its own plan instead of everything showing the 6.2 advice.

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  Check,
  ChevronDown,
  Droplets,
  FlaskConical,
  Leaf,
  SunMedium,
  ThermometerSun,
  Waves,
} from 'lucide-react'

import { useSession } from '@/hooks/useSession'
import { useSoil } from '@/hooks/useSoil'
import { useSensorDetail } from '@/hooks/useSensorDetail'
import { ErrorState, EmptyState, Skeleton } from '@/components/screen-state'
import { SensorChart } from '@/components/sensor-chart'
import { errorMessage } from '@/lib/api/client'
import { formatEventTime, formatMeasuredAt } from '@/lib/format'
import type { RangeKey, SensorSlug } from '@/lib/api/types'

const TABS: { slug: SensorSlug; label: string; icon: typeof ThermometerSun }[] = [
  { slug: 'temperature', label: 'Nhiệt độ', icon: ThermometerSun },
  { slug: 'air-humidity', label: 'Độ ẩm KK', icon: Droplets },
  { slug: 'soil-moisture', label: 'Độ ẩm đất', icon: Waves },
  { slug: 'light', label: 'Ánh sáng', icon: SunMedium },
  { slug: 'ph', label: 'pH', icon: FlaskConical },
]

const RANGES: { key: RangeKey; label: string }[] = [
  { key: '24H', label: '24H' },
  { key: '7D', label: '7 Ngày' },
  { key: '30D', label: '30 Ngày' },
]

export default function PHAnalysisPage() {
  const router = useRouter()
  const { gardenId, isBootstrapping, isAuthenticated } = useSession()
  const { latest, phScale, fertilizedToday, isLoading, isSaving, error, toggleFertilizedToday, refetch } =
    useSoil(gardenId)
  const { series, events, range, setRange, isRangeChanging } = useSensorDetail(gardenId, 'ph')
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) router.replace('/login')
  }, [isBootstrapping, isAuthenticated, router])

  async function handleMark() {
    setActionError(null)
    try {
      await toggleFertilizedToday()
    } catch (e) {
      setActionError(errorMessage(e))
    }
  }

  // pH 0–14 mapped onto the gradient bar's width.
  const markerLeft = latest ? `${Math.min(100, Math.max(0, (latest.ph / 14) * 100))}%` : '50%'

  return (
    <main className="ph-analysis-shell">
      <div className="ph-analysis-content">
        <header className="sensor-detail-header">
          <Link href="/" className="sensor-back" aria-label="Quay lại">
            <ArrowLeft size={20} />
          </Link>
          <h1>Phân tích đất</h1>
          <button className="sensor-filter" aria-label="Bộ lọc">
            <ChevronDown size={18} />
          </button>
        </header>

        <nav className="sensor-selector" aria-label="Loại cảm biến">
          {TABS.map(({ slug, label, icon: Icon }) => (
            <Link key={slug} href={`/sensor/${slug}`} className={slug === 'ph' ? 'active' : ''}>
              <Icon size={14} />
              {label}
            </Link>
          ))}
        </nav>

        {isLoading ? (
          <div className="gs-skeleton-stack">
            <Skeleton height={170} radius={20} />
            <Skeleton height={150} radius={18} />
            <Skeleton height={120} radius={18} />
          </div>
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : !latest ? (
          <EmptyState
            title="Chưa có dữ liệu pH"
            body="Gắn cảm biến pH vào vườn, hoặc nhập kết quả đo bằng bộ test thủ công."
          />
        ) : (
          <>
            <section className="ph-hero">
              <div className="sensor-hero-label">
                <FlaskConical size={19} />
                <span>Độ pH hiện tại</span>
              </div>
              <strong>{latest.ph.toFixed(1)}</strong>
              <div className="ph-scale">
                <span className="ph-marker" style={{ left: markerLeft }} />
              </div>
              <div className="ph-scale-labels">
                <span>0</span>
                <span>7</span>
                <span>14</span>
              </div>
              <div className="ph-badge">{latest.zoneLabel}</div>
              <p>Đo lần cuối: {formatMeasuredAt(latest.measuredAt)}</p>
            </section>

            <article className="ph-card recommendation-card">
              <div className="ph-card-heading">
                <div className="ph-heading-icon">
                  <Leaf size={18} />
                </div>
                <div>
                  <h2>Khuyến nghị phân bón</h2>
                  <p>Dựa trên pH {latest.ph.toFixed(1)}</p>
                </div>
              </div>
              <div className="recommendation-main">
                <h3>{latest.recommendation.title}</h3>
                <p>{latest.recommendation.rationale}</p>
                <strong>
                  Liều lượng đề xuất: {latest.recommendation.dosage}, {latest.recommendation.frequency}
                </strong>
              </div>
              {latest.recommendation.alternatives.length > 0 && (
                <div className="option-chips">
                  {latest.recommendation.alternatives.map((alt) => (
                    <button key={alt} type="button">
                      {alt}
                    </button>
                  ))}
                </div>
              )}
            </article>

            <article className="ph-card">
              <div className="detail-section-heading">
                <div>
                  <p className="eyebrow">THAM KHẢO</p>
                  <h2>Thang đo pH đất</h2>
                </div>
                <FlaskConical size={18} />
              </div>
              <div className="reference-scale">
                <span />
              </div>
              <div className="zone-grid">
                {phScale.map((zone) => (
                  <div key={zone.zone}>
                    <b>
                      {zone.label} ({zone.from}-{zone.to})
                    </b>
                    <p>{zone.suitableFor}</p>
                  </div>
                ))}
              </div>
            </article>

            <div className="range-selector">
              {RANGES.map(({ key, label }) => (
                <button key={key} className={range === key ? 'active' : ''} onClick={() => setRange(key)}>
                  {label}
                </button>
              ))}
            </div>

            <article className="ph-card">
              <div className="detail-section-heading">
                <div>
                  <p className="eyebrow">LỊCH SỬ ĐO pH</p>
                  <h2>pH theo thời gian</h2>
                </div>
                <span>{latest.ph.toFixed(1)} pH</span>
              </div>
              {series && (
                <SensorChart series={series} type="PH" dimmed={isRangeChanging} gradientId="gs-chart-ph" />
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
                      <i className={(event.tone ?? 'GRAY').toLowerCase()} />
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
                      {event.category === 'FERTILIZER' ? <Leaf size={17} /> : <FlaskConical size={17} />}
                    </div>
                  ))}
                </div>
              )}
            </article>

            {actionError && (
              <p className="gs-inline-error" role="alert">
                {actionError}
              </p>
            )}

            <button
              className={`export-button ${fertilizedToday ? 'marked' : ''}`}
              onClick={handleMark}
              disabled={isSaving}
            >
              <Check size={17} /> {fertilizedToday ? 'Đã bón phân hôm nay' : 'Đánh dấu đã bón phân hôm nay'}
            </button>
          </>
        )}
      </div>
    </main>
  )
}
