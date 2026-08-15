'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { ArrowLeft, CalendarClock, Droplets, Loader2, Play } from 'lucide-react'

import { useSession } from '@/hooks/useSession'
import { useSchedules } from '@/hooks/useSchedules'
import { EmptyState, ErrorState, Skeleton } from '@/components/screen-state'
import { errorMessage } from '@/lib/api/client'
import { formatEventTime, formatTime } from '@/lib/format'
import type { DayCode } from '@/lib/api/types'

const DAY_LABEL: Record<DayCode, string> = {
  MON: 'T2',
  TUE: 'T3',
  WED: 'T4',
  THU: 'T5',
  FRI: 'T6',
  SAT: 'T7',
  SUN: 'CN',
}

const ALL_DAYS: DayCode[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

const RUN_STATUS_LABEL: Record<string, string> = {
  SUCCESS: 'Đã tưới',
  SKIPPED: 'Đã bỏ qua',
  FAILED: 'Thất bại',
}

export default function SchedulesPage() {
  const router = useRouter()
  const { gardenId, isBootstrapping, isAuthenticated } = useSession()
  const { schedules, actuators, isLoading, error, runningId, runNow, refetch } = useSchedules(gardenId)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) router.replace('/login')
  }, [isBootstrapping, isAuthenticated, router])

  async function handleRun(scheduleId: string) {
    setActionError(null)
    try {
      await runNow(scheduleId)
    } catch (e) {
      setActionError(errorMessage(e))
    }
  }

  return (
    <main className="sensor-detail-shell">
      <div className="sensor-detail-content">
        <header className="sensor-detail-header">
          <Link href="/" className="sensor-back" aria-label="Quay lại trang chủ">
            <ArrowLeft size={20} />
          </Link>
          <h1>Lịch tưới</h1>
          <span style={{ width: 34 }} />
        </header>

        {actionError && (
          <p className="gs-inline-error" role="alert">
            {actionError}
          </p>
        )}

        {isLoading ? (
          <div className="gs-skeleton-stack">
            <Skeleton height={120} />
            <Skeleton height={120} />
          </div>
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : schedules.length === 0 ? (
          <EmptyState
            title="Chưa có lịch tưới"
            body="Tạo lịch để hệ thống tự tưới vào giờ cố định, và tự bỏ qua khi đất còn đủ ẩm."
          />
        ) : (
          <div className="gs-schedule-list">
            {schedules.map((schedule) => {
              const pump = actuators.find((a) => a.id === schedule.actuatorId)
              const isRunning = runningId === schedule.id
              return (
                <article key={schedule.id} className={`gs-schedule${schedule.enabled ? '' : ' disabled'}`}>
                  <div className="gs-schedule-head">
                    <div className="gs-schedule-icon">
                      <CalendarClock size={18} />
                    </div>
                    <div>
                      <b>{schedule.name}</b>
                      <p>
                        <Droplets size={12} /> {pump?.name ?? 'Máy bơm'} · {schedule.durationMinutes} phút
                      </p>
                    </div>
                    <strong className="gs-schedule-time">{schedule.startTime.slice(0, 5)}</strong>
                  </div>

                  <div className="gs-schedule-days">
                    {ALL_DAYS.map((day) => (
                      <span key={day} className={schedule.daysOfWeek.includes(day) ? 'on' : ''}>
                        {DAY_LABEL[day]}
                      </span>
                    ))}
                  </div>

                  <div className="gs-schedule-meta">
                    {schedule.skipIfSoilMoistureAbove != null && (
                      <span>Bỏ qua nếu đất ẩm trên {schedule.skipIfSoilMoistureAbove}%</span>
                    )}
                    {schedule.skipIfRainForecast && <span>Bỏ qua khi dự báo mưa</span>}
                  </div>

                  <div className="gs-schedule-foot">
                    <div>
                      {schedule.nextRunAt && (
                        <small>
                          Lần tới: {formatEventTime(schedule.nextRunAt)} {formatTime(schedule.nextRunAt)}
                        </small>
                      )}
                      {schedule.lastRunStatus && (
                        <small className={schedule.lastRunStatus === 'FAILED' ? 'gs-bad' : ''}>
                          {RUN_STATUS_LABEL[schedule.lastRunStatus]}
                          {/* BE explains WHY it skipped — showing it stops "why didn't it water?" */}
                          {schedule.lastSkipReason ? ` — ${schedule.lastSkipReason}` : ''}
                        </small>
                      )}
                    </div>
                    <button onClick={() => handleRun(schedule.id)} disabled={isRunning}>
                      {isRunning ? <Loader2 size={14} className="gs-spin" /> : <Play size={14} />}
                      Tưới ngay
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </div>
    </main>
  )
}
