import { backendRequest } from '@/api/http'
import type { FlowDefinition, FlowPayload, PageResult } from '@/api/types'

export const flowApi = {
  page: (pageNum = 1, pageSize = 50) =>
    backendRequest<PageResult<FlowDefinition>>({ url: '/flow/page', method: 'POST', data: { pageNum, pageSize }, retryAfterRefresh: true }),
  detail: (flowId: number) =>
    backendRequest<FlowDefinition>({ url: '/flow/getById', method: 'POST', data: { flowId }, retryAfterRefresh: true }),
  create: (data: FlowPayload) => backendRequest<{ flowId: number; success: boolean }>({ url: '/flow/create', method: 'POST', data }),
  update: (data: FlowPayload) => backendRequest<{ flowId: number; success: boolean }>({ url: '/flow/edit', method: 'POST', data }),
  remove: (flowId: number) => backendRequest<boolean>({ url: '/flow/delete', method: 'POST', data: { flowId } }),
}
