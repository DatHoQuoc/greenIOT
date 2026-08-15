"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { SessionContext, type SessionValue } from "@/hooks/useSession"
import { getProfile, login as loginApi, logout as logoutApi } from "@/lib/api/auth/authApi"
import { getGardens } from "@/lib/api/garden/gardenApi"
import { onSessionEnded, silentRefresh } from "@/lib/auth/refreshToken"
import { tokenMemory } from "@/lib/auth/tokenMemory"
import type { Garden, User } from "@/lib/api/types"

const LAST_GARDEN_KEY = "greensense.lastGardenId"

/**
 * Owns the session for the whole app.
 *
 * Bootstrap order matters: silent-refresh first, THEN profile and gardens. Doing it the
 * other way round means the first two requests always 401 on a cold load and each one
 * triggers its own refresh — which, with a rotating refresh token, is exactly the double
 * -use the backend treats as theft.
 */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [gardens, setGardens] = useState<Garden[]>([])
  const [gardenId, setGardenId] = useState<string | null>(null)
  const [isBootstrapping, setIsBootstrapping] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const selectGarden = useCallback((id: string) => {
    setGardenId(id)
    // Remembering the choice is a convenience only — the id is not a credential, and BE
    // rejects it anyway if the account has no access.
    try {
      window.localStorage.setItem(LAST_GARDEN_KEY, id)
    } catch {
      // Private browsing can refuse localStorage; the app still works, just forgetfully.
    }
  }, [])

  useEffect(() => {
    let alive = true

    ;(async () => {
      setIsBootstrapping(true)
      setError(null)

      try {
        // Resume the session from the HttpOnly cookie if there is no token in memory yet.
        if (!tokenMemory.get()) {
          const resumed = await silentRefresh()
          if (!resumed) {
            if (alive) {
              setUser(null)
              setGardens([])
              setGardenId(null)
            }
            return
          }
        }

        const [profile, list] = await Promise.all([getProfile(), getGardens()])
        if (!alive) return

        setUser(profile)
        setGardens(list)

        const remembered = (() => {
          try {
            return window.localStorage.getItem(LAST_GARDEN_KEY)
          } catch {
            return null
          }
        })()

        const chosen = list.find((g) => g.id === remembered) ?? list[0]
        setGardenId(chosen?.id ?? null)
      } catch (e) {
        if (alive) setError(e)
      } finally {
        if (alive) setIsBootstrapping(false)
      }
    })()

    return () => {
      alive = false
    }
  }, [reloadKey])

  // The client announces a dead session (refresh rejected, or the token was reused and BE
  // revoked the family). Everything downstream keys off `isAuthenticated`.
  useEffect(() => onSessionEnded(() => {
    setUser(null)
    setGardens([])
    setGardenId(null)
  }), [])

  const signIn = useCallback(async (email: string, password: string) => {
    const auth = await loginApi(email, password)
    setUser(auth.user)
    const list = await getGardens()
    setGardens(list)
    setGardenId(list[0]?.id ?? null)
  }, [])

  const signOut = useCallback(async () => {
    await logoutApi()
    setUser(null)
    setGardens([])
    setGardenId(null)
  }, [])

  const value = useMemo<SessionValue>(
    () => ({
      user,
      gardens,
      gardenId,
      selectGarden,
      isBootstrapping,
      isAuthenticated: Boolean(user),
      error,
      signIn,
      signOut,
      refresh: () => setReloadKey((k) => k + 1),
    }),
    [user, gardens, gardenId, selectGarden, isBootstrapping, error, signIn, signOut]
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
