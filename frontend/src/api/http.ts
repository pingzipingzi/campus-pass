import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

import type { ApiResult } from '@/types/api'
import { clearLogin, getToken } from '@/utils/auth-storage'

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code?: number,
    public readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const sessionInvalidCodes = new Set([40100, 40102, 40103])

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (result.code !== 0) {
      return Promise.reject(new ApiError(result.message, result.code, response.status))
    }
    return response
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const result = error.response?.data

    const shouldClearSession =
      status === 401 && (result?.code === undefined || sessionInvalidCodes.has(result.code))

    if (shouldClearSession) {
      clearLogin()
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }

    const message = result?.message ?? (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络请求失败')
    return Promise.reject(new ApiError(message, result?.code, status))
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<ApiResult<T>> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data
}
