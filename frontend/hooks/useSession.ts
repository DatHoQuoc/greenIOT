"use client"

// useSession — who is signed in, and which garden we are looking at.
//
// On a cold load the access token is NOT in memory (it never touches storage, by design),
// so the first thing this does is a silent refresh: if the HttpOnly cookie is still valid
// the session resumes invisibly, otherwise the app knows it is signed out. That single
// call is why no screen needs to handle "token missing on first paint".

import { createContext, useContext } from "react"
import type { Garden, User } from "@/lib/api/types"

export interface SessionValue {
  user: User | null
  gardens: Garden[]
  /** The garden every screen reads from. Null while loading or when signed out. */
  gardenId: string | null
  selectGarden: (gardenId: string) => void
  /** True until the initial silent refresh settles — render a skeleton, not a login form. */
  isBootstrapping: boolean
  isAuthenticated: boolean
  error: unknown
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
  refresh: () => void
}

export const SessionContext = createContext<SessionValue | null>(null)

export function useSession(): SessionValue {
  const value = useContext(SessionContext)
  if (!value) {
    throw new Error("useSession must be used inside <SessionProvider>")
  }
  return value
}
