import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { logout as logoutRequest } from '@/api/auth'
import type { CurrentUser, LoginVO } from '@/types/api'
import { clearLogin, getStoredUser, getToken, setLogin, setStoredUser } from '@/utils/auth-storage'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<CurrentUser | null>(getStoredUser())
  const token = ref<string | null>(getToken())

  const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))
  const isAdmin = computed(() => currentUser.value?.role === 1)

  function saveLogin(login: LoginVO): void {
    setLogin(login)
    token.value = login.token
    const { token: _token, ...user } = login
    currentUser.value = user
  }

  function clearSession(): void {
    clearLogin()
    token.value = null
    currentUser.value = null
  }

  function updateUserName(userName: string): void {
    if (!currentUser.value) return
    currentUser.value = { ...currentUser.value, userName }
    setStoredUser(currentUser.value)
  }

  async function logout(): Promise<void> {
    try {
      await logoutRequest()
    } finally {
      clearSession()
    }
  }

  return { currentUser, token, isLoggedIn, isAdmin, saveLogin, updateUserName, clearSession, logout }
})
