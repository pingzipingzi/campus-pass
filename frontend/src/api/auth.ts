import { request } from '@/api/http'
import type {
  ApiResult,
  CodeLoginDTO,
  ForgetPasswordDTO,
  LoginVO,
  PasswordLoginDTO,
  SignUpDTO,
} from '@/types/api'

export function loginWithPassword(data: PasswordLoginDTO): Promise<ApiResult<LoginVO>> {
  return request<LoginVO>({
    url: '/auth/login/password',
    method: 'POST',
    data,
  })
}

export function loginWithCode(data: CodeLoginDTO): Promise<ApiResult<LoginVO>> {
  return request<LoginVO>({
    url: '/auth/login/code',
    method: 'POST',
    data,
  })
}

export function sendCode(phone: string): Promise<ApiResult<void>> {
  return request<void>({
    url: '/auth/code',
    method: 'POST',
    params: { phone },
  })
}

export function logout(): Promise<ApiResult<void>> {
  return request<void>({
    url: '/auth/logout',
    method: 'POST',
  })
}

export function signUp(data: SignUpDTO): Promise<ApiResult<void>> {
  return request<void>({
    url: '/auth/signup',
    method: 'POST',
    data,
  })
}

export function resetPassword(data: ForgetPasswordDTO): Promise<ApiResult<void>> {
  return request<void>({
    url: '/auth/password/forget',
    method: 'PUT',
    data,
  })
}
