// lib/auth/refreshToken.ts
// Silent refresh: swap the HttpOnly refresh cookie for a new access token.
//
// The cookie is sent by the browser (credentials: "include"); this code never sees it.
// BE rotates it on every call — the presented token is spent, and replaying a spent one
// is treated as theft and kills the whole session. So the ONE thing this module must
// guarantee is that two concurrent 401s do not both call /refresh: the second call would
// present an already-rotated cookie and log the user out. Hence the shared in-flight
// promise below.

import { tokenMemory } from "@/lib/auth/tokenMemory"

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"

let inFlight: Promise<boolean> | null = null

/** Listeners that want to know the session ended (redirect to login, clear stores…). */
const sessionEndedHandlers = new Set<() => void>()

export function onSessionEnded(handler: () => void): () => void {
  sessionEndedHandlers.add(handler)
  return () => sessionEndedHandlers.delete(handler)
}

function announceSessionEnded() {
  tokenMemory.clear()
  sessionEndedHandlers.forEach((handler) => handler())
}

/**
 * @returns true when a new access token is in memory, false when the session is over.
 *          Callers should NOT retry on false — the refresh cookie is gone or revoked.
 */
export function silentRefresh(): Promise<boolean> {
  // Every caller that arrives while a refresh is running awaits the same promise, so the
  // rotating cookie is only ever presented once.
  if (inFlight) return inFlight

  inFlight = (async () => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: "POST",
        credentials: "include",
      })

      if (!res.ok) {
        announceSessionEnded()
        return false
      }

      const body = await res.json()
      const token = body?.data?.accessToken
      if (!token) {
        announceSessionEnded()
        return false
      }

      tokenMemory.set(token)
      return true
    } catch {
      // Network failure is not the same as a rejected session, but from the caller's
      // point of view there is still no usable token — let it surface as a failed retry.
      return false
    } finally {
      inFlight = null
    }
  })()

  return inFlight
}
