import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiResult, WebAccessTokenResponse } from './types'

export interface RequestOptions extends AxiosRequestConfig {
  unwrap?: boolean
  retryAfterRefresh?: boolean
}

export class AppError extends Error {
  constructor(
    message: string,
    public readonly kind: 'business' | 'auth' | 'forbidden' | 'network' | 'server',
    public readonly status?: number,
    public readonly code?: number,
  ) {
    super(message)
  }
}

const client = axios.create({ baseURL: '/api/backend', timeout: 20_000, withCredentials: true })
let accessToken: string | null = null
let refreshPromise: Promise<string> | null = null
let authExpiredHandler: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export function onAuthExpired(handler: () => void) {
  authExpiredHandler = handler
}

client.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = client
      .post<WebAccessTokenResponse>('/api/auth/web/refresh', undefined, {
        headers: { 'X-Skip-Auth': 'true' },
      })
      .then(({ data }) => {
        setAccessToken(data.accessToken)
        return data.accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

function toAppError(error: unknown) {
  if (!(error instanceof AxiosError)) return new AppError('发生未知错误', 'server')
  if (!error.response) return new AppError('无法连接服务，请确认本地后端已启动', 'network')
  const status = error.response.status
  const body = error.response.data as Record<string, unknown> | undefined
  const message = String(body?.msg ?? body?.message ?? error.message ?? '请求失败')
  if (status === 401) return new AppError(message, 'auth', status)
  if (status === 403) return new AppError('没有权限执行此操作', 'forbidden', status)
  return new AppError(message, status >= 500 ? 'server' : 'business', status)
}

export async function backendRequest<T>(options: RequestOptions): Promise<T> {
  const { unwrap = true, retryAfterRefresh = false, ...config } = options
  try {
    const response = await client.request<ApiResult<T> | T>(config)
    if (!unwrap) return response.data as T
    const envelope = response.data as ApiResult<T>
    if (envelope.code !== 1) throw new AppError(envelope.msg || '业务处理失败', 'business', 200, envelope.code)
    return envelope.data
  } catch (error) {
    const appError = error instanceof AppError ? error : toAppError(error)
    const skipAuth = config.headers && 'X-Skip-Auth' in config.headers
    if (appError.kind === 'auth' && retryAfterRefresh && !skipAuth) {
      try {
        await refreshAccessToken()
        return backendRequest<T>({ ...options, retryAfterRefresh: false })
      } catch {
        setAccessToken(null)
        authExpiredHandler?.()
      }
    }
    throw appError
  }
}

export async function restoreAccessToken() {
  try {
    return await refreshAccessToken()
  } catch {
    setAccessToken(null)
    return null
  }
}

export { client }
