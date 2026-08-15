"use client"

// Shared loading / error / empty states.
//
// Every screen shows the same three non-happy paths, and the failure mode worth avoiding
// is a page that renders "—" everywhere and looks like a garden with no sensors when the
// truth is that the API is down.

import { errorMessage } from "@/lib/api/client"

export function Skeleton({ height = 80, radius = 18 }: { height?: number; radius?: number }) {
  return (
    <div
      aria-hidden
      style={{
        height,
        borderRadius: radius,
        background: "linear-gradient(90deg,#ece6da 25%,#f5f1e8 50%,#ece6da 75%)",
        backgroundSize: "200% 100%",
        animation: "gs-shimmer 1.4s ease-in-out infinite",
      }}
    />
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="gs-state">
      <p className="gs-state-title">Không tải được dữ liệu</p>
      <p className="gs-state-body">{errorMessage(error)}</p>
      {onRetry && (
        <button type="button" className="gs-state-action" onClick={onRetry}>
          Thử lại
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, body, action }: { title: string; body?: string; action?: React.ReactNode }) {
  return (
    <div className="gs-state">
      <p className="gs-state-title">{title}</p>
      {body && <p className="gs-state-body">{body}</p>}
      {action}
    </div>
  )
}
