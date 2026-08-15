// lib/api/auth/authApi.ts
// Everything under /api/v1/auth.
//
// The refresh token never appears here: BE sets it as an HttpOnly cookie and the browser
// returns it on its own. Only the access token crosses into JS, and it lives in
// tokenMemory (never storage) so an XSS cannot lift a long-lived session.

import { client } from "@/lib/api/client"
import { tokenMemory } from "@/lib/auth/tokenMemory"
import type { User } from "@/lib/api/types"

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

export interface RegisterPayload {
  email: string
  password: string
  fullName: string
  phone?: string
}

export async function register(payload: RegisterPayload): Promise<AuthResponse> {
  const auth = await client.post<AuthResponse>("/api/v1/auth/register", payload)
  tokenMemory.set(auth.accessToken)
  return auth
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const auth = await client.post<AuthResponse>("/api/v1/auth/login", { email, password })
  tokenMemory.set(auth.accessToken)
  return auth
}

/**
 * Clearing the in-memory token BEFORE awaiting matters: if the network call hangs, the UI
 * has already stopped being able to make authenticated requests, so a "logged out" screen
 * is never backed by a still-live token.
 */
export async function logout(): Promise<void> {
  tokenMemory.clear()
  try {
    await client.post<void>("/api/v1/auth/logout")
  } catch {
    // The cookie may already be expired or revoked; the local session is gone either way.
  }
}

export function getProfile(): Promise<User> {
  return client.get<User>("/api/v1/auth/me")
}

export interface UpdateProfilePayload {
  fullName?: string
  phone?: string
  notifyByPush?: boolean
  notifyByEmail?: boolean
  /** Send BOTH or NEITHER — a half-open window is not a window. */
  quietHoursStart?: string | null
  quietHoursEnd?: string | null
}

export function updateProfile(payload: UpdateProfilePayload): Promise<User> {
  return client.put<User>("/api/v1/auth/me", payload)
}

/** Ends every other session, so the caller must send the user back to the login screen. */
export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return client.patch<void>("/api/v1/auth/me/password", { currentPassword, newPassword })
}

export function registerPushToken(token: string): Promise<User> {
  return client.post<User>("/api/v1/auth/me/push-tokens", { token })
}
