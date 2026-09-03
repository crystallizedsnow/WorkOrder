import { backendRequest, client } from '@/api/http'
import type {
  PageResult,
  WorkOrderCreateParams,
  WorkOrderDetail,
  WorkOrderPageParams,
  WorkOrderSummary,
} from '@/api/types'

interface SearchResult<T> { list: T[]; total: number; pageNum: number; pageSize: number }

export const workOrderApi = {
  page: (data: WorkOrderPageParams) =>
    backendRequest<PageResult<WorkOrderSummary>>({ url: '/workOrder/page', method: 'POST', data, retryAfterRefresh: true }),
  search: (keyword: string, pageNum: number, pageSize: number) =>
    backendRequest<SearchResult<WorkOrderSummary>>({
      url: '/workOrder/search', method: 'GET', params: { keyword, pageNum, pageSize }, retryAfterRefresh: true,
    }),
  detail: (id: number) =>
    backendRequest<WorkOrderDetail>({ url: '/workOrder/detail', method: 'POST', data: { id }, retryAfterRefresh: true }),
  create: (data: WorkOrderCreateParams) => backendRequest<void>({ url: '/workOrder/create', method: 'POST', data }),
  handle: (data: { id: number; handleType: number; assignedUserId?: number; remark?: string }) =>
    backendRequest<{ success: boolean; id: number; code: string }>({ url: '/workOrder/handle', method: 'POST', data }),
  approve: (data: { id: number; isApproved: boolean; remark?: string }) =>
    backendRequest<void>({ url: '/workOrder/approval', method: 'POST', data }),
  cancel: (id: number) => backendRequest<void>({ url: '/workOrder/cancel', method: 'POST', data: { id } }),
  remove: (id: number) => backendRequest<void>({ url: '/workOrder/delete', method: 'POST', data: { id } }),
  async download(kind: 'export' | 'print', data: WorkOrderPageParams | { id: number }) {
    const response = await client.post(`/workOrder/${kind}`, data, { responseType: 'blob' })
    const blobUrl = URL.createObjectURL(response.data)
    const filename = kind === 'export' ? `工单导出-${Date.now()}.xlsx` : `工单-${Date.now()}.pdf`
    if (kind === 'print') window.open(blobUrl, '_blank', 'noopener,noreferrer')
    else {
      const anchor = document.createElement('a')
      anchor.href = blobUrl
      anchor.download = filename
      anchor.click()
    }
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000)
  },
}
