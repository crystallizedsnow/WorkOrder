import { backendRequest } from '@/api/http'
import type { UserProfile, WebAccessTokenResponse } from '@/api/types'

export const authApi = {
  login: (phone: string, password: string) =>
    backendRequest<WebAccessTokenResponse>({
      url: '/api/auth/web/login',
      method: 'POST',
      data: { phone, password },
      unwrap: false,
      headers: { 'X-Skip-Auth': 'true' },
    }),
  logout: () =>
    backendRequest<void>({ url: '/api/auth/web/logout', method: 'POST', unwrap: false }),
  me: () => backendRequest<UserProfile>({ url: '/user/me', method: 'POST', retryAfterRefresh: true }),
  updateProfile: (data: { password?: string; phone?: string; email?: string }) =>
    backendRequest<void>({ url: '/user/change', method: 'POST', data }),
}
