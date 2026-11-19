import academia from 'virtual:academia'

// VITE_API_URL tem prioridade. Sem ela, o build usa a API da academia (academia.json) e o
// modo dev usa o back-end local.
export const BASE = (import.meta.env.VITE_API_URL || (import.meta.env.PROD && academia.apiUrl) || 'http://localhost:8080')
  .replace(/\/$/, '')
const TOKEN_KEY = 'academia.token'

export const tokenStore = {
  get: () => { try { return localStorage.getItem(TOKEN_KEY) } catch { return null } },
  set: (t) => { try { localStorage.setItem(TOKEN_KEY, t) } catch { /* modo privado */ } },
  clear: () => { try { localStorage.removeItem(TOKEN_KEY) } catch { /* modo privado */ } },
}

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) { onUnauthorized = fn }

export class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status }
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(`${BASE}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, 'Não foi possível conectar ao servidor.')
  }
  if (res.status === 401 && !path.endsWith('/auth/login')) {
    onUnauthorized()
    throw new ApiError(401, 'Sessão expirada. Entre novamente.')
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(res.status, data?.detail || data?.message || `Erro ${res.status}`)
  }
  return data
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, b) => request('POST', p, b ?? {}),
  put: (p, b) => request('PUT', p, b),
  del: (p) => request('DELETE', p),
}

export function qs(params) {
  const s = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, v) })
  const str = s.toString()
  return str ? `?${str}` : ''
}
