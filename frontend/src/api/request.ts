import { request } from '@/api/http'
import type { ApiResult, PageResult, RequestVO } from '@/types/api'

export function getMyRequests(page: number): Promise<ApiResult<PageResult<RequestVO>>> {
  return request({
    url: '/request/mine',
    method: 'GET',
    params: { pages: page },
  })
}
