import type { CurrentUser, LoginVO } from '@/types/api'

const TOKEN_KEY = 'campus-pass-access-token'
const USER_KEY = 'campus-pass-current-user'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setLogin(login: LoginVO): void {
  localStorage.setItem(TOKEN_KEY, login.token)
  const { token: _token, ...currentUser } = login
  setStoredUser(currentUser)
}

export function setStoredUser(user: CurrentUser): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getStoredUser(): CurrentUser | null {
  const value = localStorage.getItem(USER_KEY)
  if (!value) return null

  try {
    return JSON.parse(value) as CurrentUser
  } catch {
    clearLogin()
    return null
  }
}

export function clearLogin(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
