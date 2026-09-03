import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/modules/auth'
import { onAuthExpired, restoreAccessToken, setAccessToken } from '@/api/http'
import type { UserProfile } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const profile = ref<UserProfile | null>(null)
  const initialized = ref(false)
  const accessTokenExpiresAt = ref<string | null>(null)
  const isAuthenticated = computed(() => Boolean(profile.value))
  const isAdmin = computed(() => profile.value?.role?.toLowerCase() === 'admin')

  async function loadProfile() {
    profile.value = await authApi.me()
  }

  async function login(phone: string, password: string) {
    const token = await authApi.login(phone, password)
    setAccessToken(token.accessToken)
    accessTokenExpiresAt.value = token.accessTokenExpiresAt
    await loadProfile()
  }

  async function restoreSession() {
    if (initialized.value) return
    try {
      const token = await restoreAccessToken()
      if (token) await loadProfile()
    } finally {
      initialized.value = true
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearSession()
    }
  }

  function clearSession() {
    setAccessToken(null)
    profile.value = null
    accessTokenExpiresAt.value = null
  }

  onAuthExpired(clearSession)
  return { profile, initialized, isAuthenticated, isAdmin, login, logout, restoreSession, loadProfile, clearSession }
})
