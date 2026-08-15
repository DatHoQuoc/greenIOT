'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  Bell,
  ChevronRight,
  Droplets,
  Fan,
  Flower2,
  Gauge,
  History,
  House,
  Lightbulb,
  Settings,
  Sprout,
  SunMedium,
  ThermometerSun,
  Waves,
  Wind,
} from 'lucide-react'

import { useSession } from '@/hooks/useSession'
import { useDashboard } from '@/hooks/useDashboard'
import { ErrorState, Skeleton } from '@/components/screen-state'
import { SensorChart } from '@/components/sensor-chart'
import { errorMessage } from '@/lib/api/client'
import { getSeries } from '@/lib/api/sensor/sensorApi'
import { formatWithUnit } from '@/lib/format'
import type { ActuatorType, SensorTile, SensorType, Series } from '@/lib/api/types'

// Icon and colour are presentation-only, so they stay on the client keyed by the BE enum.
// Everything else on this screen — values, labels, breach state, counts — comes from the API.
const SENSOR_ICON: Record<SensorType, typeof ThermometerSun> = {
  TEMPERATURE: ThermometerSun,
  AIR_HUMIDITY: Droplets,
  SOIL_MOISTURE: Waves,
  LIGHT: SunMedium,
  PH: Sprout,
}

const SENSOR_TONE: Record<SensorType, string> = {
  TEMPERATURE: 'sage',
  AIR_HUMIDITY: 'mint',
  SOIL_MOISTURE: 'sand',
  LIGHT: 'sun',
  PH: 'sage',
}

const ACTUATOR_ICON: Record<ActuatorType, typeof Droplets> = {
  WATER_PUMP: Droplets,
  CURTAIN: Wind,
  FAN: Fan,
  GROW_LIGHT: Lightbulb,
  VALVE: Droplets,
}

function SensorCard({ tile }: { tile: SensorTile }) {
  const Icon = SENSOR_ICON[tile.type] ?? Gauge
  return (
    <Link
      href={`/sensor/${tile.slug}`}
      className={`sensor-card ${SENSOR_TONE[tile.type] ?? 'sage'}${tile.breached ? ' breached' : ''}`}
      aria-label={`Xem chi tiết ${tile.label}`}
    >
      <div className="sensor-icon">
        <Icon size={19} strokeWidth={1.8} />
      </div>
      <div className="sensor-value">{formatWithUnit(tile.value, tile.type, tile.unit)}</div>
      <p>{tile.label}</p>
    </Link>
  )
}

/**
 * The soil-moisture card on the home screen. It fetches its own 7-day series rather than
 * riding on the dashboard payload: the dashboard is one round trip for the whole screen,
 * and stuffing a chart series into it would make that call heavy for every other consumer.
 */
function MoistureChart({ gardenId, tile }: { gardenId: string; tile: SensorTile | undefined }) {
  const [series, setSeries] = useState<Series | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!tile) return
    let alive = true
    getSeries(gardenId, 'soil-moisture', '7D')
      .then((data) => alive && setSeries(data))
      .catch(() => alive && setFailed(true))
    return () => {
      alive = false
    }
  }, [gardenId, tile])

  if (!tile) return null

  return (
    <article className="chart-card">
      <div className="section-heading">
        <div>
          <p className="eyebrow">THEO DÕI 7 NGÀY</p>
          <h2>Độ ẩm đất</h2>
        </div>
        <span className="chart-current">{formatWithUnit(tile.value, tile.type, tile.unit)}</span>
      </div>
      {series ? (
        <SensorChart series={series} type="SOIL_MOISTURE" threshold={tile.threshold} gradientId="gs-home-moisture" />
      ) : failed ? (
        <p className="gs-chart-empty">Không tải được biểu đồ</p>
      ) : (
        <Skeleton height={110} />
      )}
    </article>
  )
}

export default function Page() {
  const router = useRouter()
  const { gardenId, isBootstrapping, isAuthenticated } = useSession()
  const { dashboard, isLoading, error, isLive, refetch, toggleSystem } = useDashboard(gardenId)
  const [tab, setTab] = useState('Trạng thái')
  const [toggleError, setToggleError] = useState<string | null>(null)

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) router.replace('/login')
  }, [isBootstrapping, isAuthenticated, router])

  if (isBootstrapping || (isLoading && !dashboard)) {
    return (
      <main className="app-shell">
        <div className="app-content gs-skeleton-stack">
          <Skeleton height={44} radius={14} />
          <Skeleton height={132} radius={22} />
          <Skeleton height={190} radius={20} />
          <Skeleton height={120} radius={18} />
        </div>
      </main>
    )
  }

  if (error && !dashboard) {
    return (
      <main className="app-shell">
        <div className="app-content">
          <ErrorState error={error} onRetry={refetch} />
        </div>
      </main>
    )
  }

  if (!dashboard) return null

  const { garden, sensors, actuators, latestSoil, unreadAlerts, sensorCount, pumpCount } = dashboard
  const soilTile = sensors.find((s) => s.type === 'SOIL_MOISTURE')

  async function handleToggle() {
    setToggleError(null)
    try {
      await toggleSystem(!garden.systemEnabled)
    } catch (e) {
      setToggleError(errorMessage(e))
    }
  }

  return (
    <main className="app-shell">
      <div className="app-content">
        <header className="topbar">
          <button className="icon-button" aria-label="Quay lại">
            <ArrowLeft size={21} />
          </button>
          <h1>{garden.name}</h1>
          <Link href="/alerts" className="icon-button notification" aria-label={`Thông báo, ${unreadAlerts} chưa đọc`}>
            <Bell size={20} />
            {unreadAlerts > 0 && <span>{unreadAlerts > 99 ? '99+' : unreadAlerts}</span>}
          </Link>
        </header>

        <section className="hero-card">
          <div className="hero-topline">
            <div className="garden-mark">
              <Flower2 size={21} />
              <div>
                <strong>{garden.name}</strong>
                <small>{garden.description ?? garden.type}</small>
              </div>
            </div>
            <button
              className={`toggle ${garden.systemEnabled ? 'on' : ''}`}
              onClick={handleToggle}
              aria-label="Bật tắt hệ thống"
              aria-pressed={garden.systemEnabled}
            >
              <span />
            </button>
          </div>

          {toggleError && (
            <p className="gs-inline-error" role="alert">
              {toggleError}
            </p>
          )}

          <div className="hero-stats">
            <span>
              <strong>{sensorCount}</strong> Sensors
            </span>
            <i />
            <span>
              <strong>{pumpCount}</strong> Pumps
            </span>
            <i />
            {/* Live = the WebSocket is actually connected, not a decoration. */}
            <span className={isLive ? 'live' : 'live off'}>
              <b /> {isLive ? 'Live' : 'Offline'}
            </span>
          </div>

          <div className="hero-tabs" role="tablist">
            {['Trạng thái', 'Lịch tưới', 'Cảnh báo'].map((item) => (
              <button
                key={item}
                className={tab === item ? 'active' : ''}
                onClick={() => {
                  setTab(item)
                  if (item === 'Lịch tưới') router.push('/schedules')
                  if (item === 'Cảnh báo') router.push('/alerts')
                }}
                role="tab"
                aria-selected={tab === item}
              >
                {item}
                {item === 'Cảnh báo' && unreadAlerts > 0 && <b />}
              </button>
            ))}
          </div>
        </section>

        <section className="sensor-grid" aria-label="Thông số cảm biến">
          {sensors.map((tile) => (
            <SensorCard key={tile.sensorId} tile={tile} />
          ))}
        </section>

        <section className="automation-row" aria-label="Trạng thái tự động hóa">
          {actuators.map((actuator) => {
            const Icon = ACTUATOR_ICON[actuator.type] ?? Droplets
            const active = actuator.state === 'ON' || actuator.state === 'OPEN'
            return (
              <span key={actuator.id} className={`pill ${active ? 'green' : 'gray'}`}>
                <Icon size={13} /> {actuator.typeLabel}: <b>{actuator.stateLabel}</b>
              </span>
            )
          })}
        </section>

        {latestSoil && (
          <article className="soil-card">
            <div className="soil-symbol">
              <Sprout size={22} />
            </div>
            <div className="soil-copy">
              <p className="eyebrow">PHÂN TÍCH ĐẤT</p>
              <div className="soil-ph">pH {latestSoil.ph.toFixed(1)}</div>
              <p>{latestSoil.zoneLabel}</p>
            </div>
            <Link href="/sensor/ph" className="details-link" aria-label="Xem phân tích đất chi tiết">
              Xem chi tiết <ChevronRight size={16} />
            </Link>
            {/* Derived server-side from the pH band — not a hardcoded string any more. */}
            <p className="recommendation">
              <Lightbulb size={15} /> Khuyến nghị: {latestSoil.recommendation.title}
            </p>
          </article>
        )}

        {gardenId && <MoistureChart gardenId={gardenId} tile={soilTile} />}
      </div>

      <nav className="bottom-nav" aria-label="Điều hướng chính">
        <button className="selected">
          <House size={21} />
          <span>Trang chủ</span>
        </button>
        <Link href={`/sensor/${sensors[0]?.slug ?? 'temperature'}`} className="bottom-nav-link">
          <Gauge size={21} />
          <span>Cảm biến</span>
        </Link>
        <Link href="/alerts" className="bottom-nav-link">
          <History size={21} />
          <span>Lịch sử</span>
        </Link>
        <Link href="/settings" className="bottom-nav-link">
          <Settings size={21} />
          <span>Cài đặt</span>
        </Link>
      </nav>
    </main>
  )
}
