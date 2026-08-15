// lib/api/client.ts
// Thin HTTP client wrapping fetch.
//
// Features:
//   - Attaches Bearer token from tokenMemory on every request
//   - Sends cookies (credentials: "include") for the HttpOnly refresh cookie
//   - On 401: silently refreshes the access token and retries ONCE
//   - Unwraps the ApiResponse envelope so callers get `data` directly
//   - Throws ApiError for non-OK responses

import { tokenMemory } from "@/lib/auth/tokenMemory"
import { silentRefresh } from "@/lib/auth/refreshToken"

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"

/**
 * Every endpoint answers in this envelope, so unwrapping happens once here rather than
 * `.data` appearing at 200 call sites.
 */
interface ApiEnvelope<T> {
  success: boolean
  data: T
  error?: { code: string; message: string; details?: unknown }
  timestamp: string
}

// ── Core request function ──────────────────────────────────────────────────

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const token = tokenMemory.get()

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: "include", // Always send the HttpOnly refresh cookie
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      // Caller-provided headers override defaults (e.g. Content-Type for multipart)
      ...init.headers,
    },
  })

  // 401 means "your token expired, refresh and retry". 403 means "this will never work"
  // — BE separates them deliberately, so retrying on 403 would be a pointless round trip
  // and an infinite loop risk.
  if (res.status === 401 && retry) {
    const refreshed = await silentRefresh()
    if (!refreshed) {
      throw new ApiError(401, { error: { code: "SESSION_EXPIRED", message: "Phiên đăng nhập đã hết hạn" } })
    }
    return request<T>(path, init, false) // retry=false prevents an infinite loop
  }

  if (!res.ok) {
    let body: unknown = {}
    try {
      body = await res.json()
    } catch {
      // Body may be empty (e.g. 204) — ignore the parse error
    }
    throw new ApiError(res.status, body)
  }

  if (res.status === 204) {
    return {} as T
  }

  const envelope = (await res.json()) as ApiEnvelope<T>
  return envelope.data
}

// ── Public surface ─────────────────────────────────────────────────────────

/**
 * Tải một file (CSV/JSON…) về dạng Blob.
 *
 * Vì sao không dùng thẻ `<a href>`: token là Bearer giữ trong bộ nhớ, mà điều hướng của
 * trình duyệt không mang được header Authorization → server trả 401 và người dùng nhận
 * một file lỗi thay vì dữ liệu. Phải đi qua fetch để gắn token, kèm luôn cơ chế
 * silent-refresh giống mọi request khác.
 */
async function requestBlob(path: string, retry = true): Promise<Blob> {
  const token = tokenMemory.get()
  const res = await fetch(`${API_BASE}${path}`, {
    method: "GET",
    credentials: "include",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })

  if (res.status === 401 && retry) {
    const refreshed = await silentRefresh()
    if (!refreshed) throw new ApiError(401, {})
    return requestBlob(path, false)
  }
  if (!res.ok) throw new ApiError(res.status, {})
  return res.blob()
}

export const client = {
  get<T>(path: string, init?: RequestInit): Promise<T> {
    return request<T>(path, { ...init, method: "GET" })
  },

  getBlob(path: string): Promise<Blob> {
    return requestBlob(path)
  },

  post<T>(path: string, body?: unknown, init?: RequestInit): Promise<T> {
    return request<T>(path, {
      ...init,
      method: "POST",
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  },

  put<T>(path: string, body: unknown, init?: RequestInit): Promise<T> {
    return request<T>(path, { ...init, method: "PUT", body: JSON.stringify(body) })
  },

  patch<T>(path: string, body: unknown, init?: RequestInit): Promise<T> {
    return request<T>(path, { ...init, method: "PATCH", body: JSON.stringify(body) })
  },

  delete<T>(path: string, init?: RequestInit): Promise<T> {
    return request<T>(path, { ...init, method: "DELETE" })
  },
}

// ── Error type ─────────────────────────────────────────────────────────────

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: unknown
  ) {
    super(`HTTP ${status}`)
    this.name = "ApiError"
  }

  /** BE's machine-readable code, e.g. ALREADY_IN_STATE — for branching on a failure. */
  get code(): string | null {
    const body = this.body as { error?: { code?: string } } | undefined
    return body?.error?.code ?? null
  }

  /** BE's human message; already Vietnamese where it faces a user. */
  get detail(): string | null {
    const body = this.body as { error?: { message?: string } } | undefined
    return body?.error?.message ?? null
  }
}

/** Vietnamese copy for the failures a screen actually shows. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
    if (error.status === 403) return "Bạn không có quyền thực hiện thao tác này."
    if (error.status === 404) return "Không tìm thấy dữ liệu."
    // 409s carry a domain reason worth showing verbatim (cooldown, already running…).
    if (error.detail) return error.detail
    if (error.status >= 500) return "Máy chủ đang gặp sự cố. Vui lòng thử lại sau."
  }
  return "Không kết nối được máy chủ. Kiểm tra kết nối mạng."
}
