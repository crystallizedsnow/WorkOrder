import { backendRequest } from '@/api/http'
import type {
  DashboardData,
  MessageItem,
  PageResult,
  StatisticItem,
  WeekHandleItem,
  WorkOrderTodo,
} from '@/api/types'

export const dashboardApi = {
  data: () => backendRequest<DashboardData>({ url: '/dashboard/data', method: 'POST', retryAfterRefresh: true }),
  todo: () => backendRequest<WorkOrderTodo[]>({ url: '/dashboard/todo', method: 'POST', retryAfterRefresh: true }),
  status: (timeType = 2) =>
    backendRequest<StatisticItem[]>({ url: '/dashboard/status', method: 'POST', data: { timeType }, retryAfterRefresh: true }),
  type: (timeType = 2) =>
    backendRequest<StatisticItem[]>({ url: '/dashboard/type', method: 'POST', data: { timeType }, retryAfterRefresh: true }),
  week: () => backendRequest<WeekHandleItem[]>({ url: '/dashboard/handleQuantity', method: 'POST', retryAfterRefresh: true }),
  messages: (pageNum = 1, pageSize = 10) =>
    backendRequest<PageResult<MessageItem>>({
      url: '/dashboard/pageMessages', method: 'POST', data: { pageNum, pageSize }, retryAfterRefresh: true,
    }),
}
