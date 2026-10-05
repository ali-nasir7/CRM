import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'

export interface ApiErrorShape {
  code: string
  message: string
  details?: Record<string, string>
  traceId?: string
  timestamp?: string
}

export const api = axios.create({
  baseURL: '/api/v1',
  // Let Axios/browser choose the request content type. In particular, FormData
  // needs a browser-generated multipart boundary rather than a JSON header.
  headers: { Accept: 'application/json' },
})

export const TOKEN_KEY = 'crm.accessToken'
export const REFRESH_KEY = 'crm.refreshToken'

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let refreshing: Promise<string | null> | null = null

async function tryRefresh(): Promise<string | null> {
  const refresh = localStorage.getItem(REFRESH_KEY)
  if (!refresh) return null
  try {
    const res = await axios.post('/api/v1/auth/refresh', { refreshToken: refresh })
    const data = res.data
    localStorage.setItem(TOKEN_KEY, data.accessToken)
    localStorage.setItem(REFRESH_KEY, data.refreshToken)
    return data.accessToken as string
  } catch {
    return null
  }
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    // /auth/login and /auth/refresh manage their own tokens; EVERYTHING else (including
    // /auth/me) may transparently refresh an expired access token. This is what prevents
    // the "session dies after 15 idle minutes / data missing until refresh" failure mode.
    const isAuthEndpoint = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh')
    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true
      refreshing = refreshing ?? tryRefresh()
      const token = await refreshing
      refreshing = null
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      }
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
      if (!window.location.pathname.startsWith('/login')) window.location.replace('/login')
    }
    return Promise.reject(error)
  },
)

export function apiError(err: unknown): ApiErrorShape {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data
    if (data && typeof data === 'object' && 'message' in data) return data as ApiErrorShape
    if (err.response) {
      return { code: `HTTP_${err.response.status}`, message: `Server returned ${err.response.status} ${err.response.statusText || ''}` }
    }
    return { code: 'NETWORK', message: 'Cannot reach the server. Check that the backend is running on port 8080.' }
  }
  return { code: 'UNKNOWN', message: err instanceof Error ? err.message : 'Something went wrong. Please try again.' }
}
