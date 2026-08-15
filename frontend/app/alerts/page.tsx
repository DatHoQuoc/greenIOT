'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'
import { AlertTriangle, ArrowLeft, Check, CheckCheck, Info, OctagonAlert } from 'lucide-react'

import { useSession } from '@/hooks/useSession'
import { useAlerts } from '@/hooks/useAlerts'
import { EmptyState, ErrorState, Skeleton } from '@/components/screen-state'
import { formatEventTime, formatTime } from '@/lib/format'
import type { AlertSeverity } from '@/lib/api/types'

const SEVERITY_ICON: Record<AlertSeverity, typeof Info> = {
  INFO: Info,
  WARNING: AlertTriangle,
  CRITICAL: OctagonAlert,
}

const SEVERITY_LABEL: Record<AlertSeverity, string> = {
  INFO: 'Thông tin',
  WARNING: 'Cảnh báo',
  CRITICAL: 'Nghiêm trọng',
}

export default function AlertsPage() {
  const router = useRouter()
  const { gardenId, isBootstrapping, isAuthenticated } = useSession()
  const { alerts, unreadCount, page, totalPages, setPage, filters, setFilters, isLoading, isPaging, error, markRead, acknowledge, markAllRead, refetch } =
    useAlerts(gardenId)

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) router.replace('/login')
  }, [isBootstrapping, isAuthenticated, router])

  return (
    <main className="sensor-detail-shell">
      <div className="sensor-detail-content">
        <header className="sensor-detail-header">
          <Link href="/" className="sensor-back" aria-label="Quay lại trang chủ">
            <ArrowLeft size={20} />
          </Link>
          <h1>Cảnh báo</h1>
          <button
            className="sensor-filter"
            aria-label="Đánh dấu tất cả đã đọc"
            onClick={() => markAllRead()}
            disabled={unreadCount === 0}
          >
            <CheckCheck size={18} />
          </button>
        </header>

        <div className="range-selector" role="tablist" aria-label="Bộ lọc cảnh báo">
          <button
            role="tab"
            aria-selected={!filters.unreadOnly && !filters.status}
            className={!filters.unreadOnly && !filters.status ? 'active' : ''}
            onClick={() => setFilters({})}
          >
            Tất cả
          </button>
          <button
            role="tab"
            aria-selected={Boolean(filters.unreadOnly)}
            className={filters.unreadOnly ? 'active' : ''}
            onClick={() => setFilters({ unreadOnly: true })}
          >
            Chưa đọc
          </button>
          <button
            role="tab"
            aria-selected={filters.status === 'OPEN'}
            className={filters.status === 'OPEN' ? 'active' : ''}
            onClick={() => setFilters({ status: 'OPEN' })}
          >
            Đang mở
          </button>
        </div>

        {isLoading ? (
          <div className="gs-skeleton-stack">
            <Skeleton height={82} />
            <Skeleton height={82} />
            <Skeleton height={82} />
          </div>
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : alerts.length === 0 ? (
          <EmptyState title="Không có cảnh báo" body="Khu vườn đang hoạt động bình thường." />
        ) : (
          <div className="gs-alert-list" style={{ opacity: isPaging ? 0.5 : 1 }}>
            {alerts.map((alert) => {
              const Icon = SEVERITY_ICON[alert.severity]
              return (
                <article
                  key={alert.id}
                  className={`gs-alert gs-alert-${alert.severity.toLowerCase()}${alert.read ? '' : ' unread'}`}
                >
                  <div className="gs-alert-icon">
                    <Icon size={17} />
                  </div>
                  <div className="gs-alert-body">
                    <div className="gs-alert-top">
                      <b>{alert.title}</b>
                      <time dateTime={alert.raisedAt}>
                        {formatEventTime(alert.raisedAt)} {formatTime(alert.raisedAt)}
                      </time>
                    </div>
                    <p>{alert.message}</p>
                    <div className="gs-alert-meta">
                      <span className="gs-alert-tag">{SEVERITY_LABEL[alert.severity]}</span>
                      {alert.status === 'RESOLVED' && <span className="gs-alert-tag resolved">Đã tự khắc phục</span>}
                      {alert.status === 'ACKNOWLEDGED' && <span className="gs-alert-tag ack">Đã tiếp nhận</span>}
                    </div>
                  </div>
                  <div className="gs-alert-actions">
                    {!alert.read && (
                      <button onClick={() => markRead(alert.id)} aria-label="Đánh dấu đã đọc">
                        <Check size={15} />
                      </button>
                    )}
                    {alert.status === 'OPEN' && (
                      <button onClick={() => acknowledge(alert.id)} className="gs-ack">
                        Tiếp nhận
                      </button>
                    )}
                  </div>
                </article>
              )
            })}
          </div>
        )}

        {totalPages > 1 && (
          <div className="gs-pagination">
            <button onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0}>
              Trước
            </button>
            <span>
              Trang {page + 1} / {totalPages}
            </span>
            <button onClick={() => setPage(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1}>
              Sau
            </button>
          </div>
        )}
      </div>
    </main>
  )
}
