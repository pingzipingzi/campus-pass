import { request } from '@/api/http'
import type { ApiResult, PageResult, RegistrationVO, RequestVO, WaitListVO } from '@/types/api'

export function getRegistrationStatus(activityId: number): Promise<ApiResult<RegistrationVO>> {
  return request({
    url: `/registration/status/${activityId}`,
    method: 'GET',
  })
}

export function registerActivity(activityId: number): Promise<ApiResult<string>> {
  return request({
    url: `/registration/${activityId}`,
    method: 'POST',
  })
}

export function queryRegistrationRequest(requestId: string): Promise<ApiResult<RequestVO>> {
  return request({
    url: `/request/${requestId}`,
    method: 'GET',
  })
}

export function cancelRegistration(activityId: number): Promise<ApiResult<void>> {
  return request({
    url: `/registration/cancel/${activityId}`,
    method: 'POST',
  })
}

export function cancelWaitList(activityId: number): Promise<ApiResult<void>> {
  return request({
    url: `/wait-list/cancel/${activityId}`,
    method: 'POST',
  })
}

export function getMyRegistrations(page: number): Promise<ApiResult<PageResult<RegistrationVO>>> {
  return request({
    url: '/registration/mine',
    method: 'GET',
    params: { pages: page },
  })
}

export function getMyWaitLists(page: number): Promise<ApiResult<PageResult<WaitListVO>>> {
  return request({
    url: '/wait-list/mine',
    method: 'GET',
    params: { pages: page },
  })
}

export function getRegistrationsOfMyActivity(
  activityId: number,
  page: number,
): Promise<ApiResult<PageResult<RegistrationVO>>> {
  return request({
    url: `/registration/mine/activity/${activityId}`,
    method: 'GET',
    params: { pages: page },
  })
}

export function getWaitListOfMyActivity(
  activityId: number,
  page: number,
): Promise<ApiResult<PageResult<WaitListVO>>> {
  return request({
    url: `/wait-list/mine/activity/${activityId}`,
    method: 'GET',
    params: { pages: page },
  })
}
