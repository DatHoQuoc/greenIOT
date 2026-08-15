// lib/auth/tokenMemory.ts
// Module-level in-memory access token store.
//
// Why a separate module and not React state?
//   - The API client (client.ts) needs the token synchronously, outside any component
//     tree, so it cannot read from a hook.
//   - Module scope survives re-renders; React state does not exist outside the tree.
//   - NEVER localStorage/sessionStorage — an access token there is readable by any XSS
//     on the page and survives the tab. The refresh token is an HttpOnly cookie the
//     browser attaches on its own, which JS cannot read at all.

let _accessToken: string | null = null

export const tokenMemory = {
  get(): string | null {
    return _accessToken
  },

  set(token: string): void {
    _accessToken = token
  },

  clear(): void {
    _accessToken = null
  },
}
