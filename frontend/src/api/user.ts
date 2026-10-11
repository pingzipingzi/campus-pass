import { request } from '@/api/http'
import type { ApiResult, ChangePasswordDTO, UserVO } from '@/types/api'

export function getMyProfile(): Promise<ApiResult<UserVO>> {
  return request({ url: '/user/me', method: 'GET' })
}

export function updateMyName(name: string): Promise<ApiResult<void>> {
  return request({
    url: '/user/me',
    method: 'PATCH',
    params: { name },
  })
}

export function changePassword(data: ChangePasswordDTO): Promise<ApiResult<void>> {
  return request({
    url: '/user/me/password',
    method: 'PUT',
    data,
  })
}
