import { request } from '@/api/http'
import type { ActivityDTO, ActivityPageVO, ActivityVO, ApiResult, PageResult } from '@/types/api'

export interface ActivityQuery {
  page: number
  keyword?: string
  activityType?: number
}

export function getActivityPage(query: ActivityQuery): Promise<ApiResult<PageResult<ActivityPageVO>>> {
  const keyword = query.keyword?.trim()
  const hasType = query.activityType !== undefined

  if (keyword && hasType) {
    return request({
      url: `/activities/keyword/${query.activityType}`,
      method: 'GET',
      params: { keyword, pages: query.page },
    })
  }

  if (keyword) {
    return request({
      url: '/activities/keyword',
      method: 'GET',
      params: { keyword, pages: query.page },
    })
  }

  if (hasType) {
    return request({
      url: `/activities/activityType/${query.activityType}`,
      method: 'GET',
      params: { pages: query.page },
    })
  }

  return request({
    url: '/activities',
    method: 'GET',
    params: { pages: query.page },
  })
}

export function getActivityDetail(activityId: number): Promise<ApiResult<ActivityVO>> {
  return request({
    url: `/activities/${activityId}`,
    method: 'GET',
  })
}

export function getMyActivityPage(page: number): Promise<ApiResult<PageResult<ActivityPageVO>>> {
  return request({
    url: '/activities/mine',
    method: 'GET',
    params: { pages: page },
  })
}

export function getMyActivityDetail(activityId: number): Promise<ApiResult<ActivityVO>> {
  return request({
    url: `/activities/${activityId}/mine`,
    method: 'GET',
  })
}

export function createActivity(data: ActivityDTO): Promise<ApiResult<void>> {
  return request({
    url: '/activities',
    method: 'POST',
    data,
  })
}

export function updateActivityDraft(activityId: number, data: ActivityDTO): Promise<ApiResult<void>> {
  return request({
    url: `/activities/activityId/${activityId}`,
    method: 'PUT',
    data,
  })
}

export function publishActivity(activityId: number): Promise<ApiResult<void>> {
  return request({
    url: `/activities/${activityId}/publish`,
    method: 'POST',
  })
}

export function cancelActivity(activityId: number): Promise<ApiResult<void>> {
  return request({
    url: `/activities/${activityId}/cancel`,
    method: 'POST',
  })
}
