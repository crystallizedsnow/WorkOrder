import { backendRequest } from '@/api/http'
import type { PageResult, Staff } from '@/api/types'

export interface OrganizationNode {
  id?: number
  name?: string
  label?: string
  children?: OrganizationNode[]
  [key: string]: unknown
}

export const organizationApi = {
  tree: () => backendRequest<OrganizationNode[]>({ url: '/user/organization', method: 'POST', retryAfterRefresh: true }),
  staff: (data: Record<string, unknown> = { pageNum: 1, pageSize: 100 }) =>
    backendRequest<PageResult<Staff>>({ url: '/user/page', method: 'POST', data, retryAfterRefresh: true }),
  allStaff: () => backendRequest<Staff[]>({ url: '/user/all', method: 'POST', retryAfterRefresh: true }),
  adminStaff: (data: Record<string, unknown>) =>
    backendRequest<PageResult<Staff>>({ url: '/admin/staff/page', method: 'POST', data, retryAfterRefresh: true }),
  addStaff: (data: Record<string, unknown>) => backendRequest<void>({ url: '/admin/staff/add', method: 'POST', data }),
  updateStaff: (data: Record<string, unknown>) => backendRequest<void>({ url: '/admin/staff/change', method: 'POST', data }),
  deleteStaff: (id: number) => backendRequest<void>({ url: '/admin/staff/delete', method: 'POST', params: { id } }),
  companies: () => backendRequest<Record<string, unknown>[]>({ url: '/admin/company/all', method: 'POST', retryAfterRefresh: true }),
  addCompany: (data: Record<string, unknown>) => backendRequest<void>({ url: '/admin/company/add', method: 'POST', data }),
  deleteCompany: (id: number) => backendRequest<void>({ url: '/admin/company/delete', method: 'POST', params: { id } }),
  departments: (companyName: string) =>
    backendRequest<Record<string, unknown>[]>({ url: '/admin/department/all', method: 'POST', params: { companyName }, retryAfterRefresh: true }),
  addDepartment: (data: Record<string, unknown>) => backendRequest<void>({ url: '/admin/department/add', method: 'POST', data }),
  updateDepartment: (data: Record<string, unknown>) => backendRequest<void>({ url: '/admin/department/change', method: 'POST', data }),
  deleteDepartment: (id: number) => backendRequest<void>({ url: '/admin/department/delete', method: 'POST', params: { id } }),
}
