import { request } from '@/api/http'
import type { ActivityPageVO, ApiResult, PageResult, UserVO } from '@/types/api'

export function getAdminUsers(page: number): Promise<ApiResult<PageResult<UserVO>>> {
  return request({
    url: '/admin/users',
    method: 'GET',
    params: { pages: page },
  })
}

export function disableUser(userId: number): Promise<ApiResult<void>> {
  return request({ url: `/admin/disable/${userId}`, method: 'POST' })
}

export function enableUser(userId: number): Promise<ApiResult<void>> {
  return request({ url: `/admin/enable/${userId}`, method: 'POST' })
}

export function getAdminActivities(page: number): Promise<ApiResult<PageResult<ActivityPageVO>>> {
  return request({
    url: '/admin/activity',
    method: 'GET',
    params: { pages: page },
  })
}

export function forceCancelActivity(activityId: number): Promise<ApiResult<void>> {
  return request({ url: `/admin/activity/cancel/${activityId}`, method: 'POST' })
}
