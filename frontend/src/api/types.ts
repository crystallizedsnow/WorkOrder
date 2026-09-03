export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages?: number
}

export interface WebAccessTokenResponse {
  tokenType: string
  accessToken: string
  accessTokenExpiresAt: string
}

export interface UserProfile {
  userId: string
  name: string
  staffNumber: string
  companyCode: string
  companyName: string
  departmentCode: string
  departmentName: string
  position: string
  managerNumber?: string
  managerName?: string
  phone: string
  email: string
  role: 'admin' | 'user' | string
  createTime: string
  updateTime: string
}

export interface Staff {
  id: number
  staffNumber: string
  name: string
  companyCode: string
  company: string
  departmentCode: string
  department: string
  position: string
  status: number
  managerNumber?: string
  managerName?: string
  phone: string
  email: string
  role?: string
}

export interface HandlerInfo {
  id: number
  orderId: number
  userId: number
  handleType: number
  handleTypeDesc: string
  finished: boolean
  finishedDesc: string
  userName: string
  companyCode: string
  companyName: string
  departmentCode: string
  departmentName: string
  handleTime?: string
  createTime?: string
  updateTime?: string
  remark?: string
}

export interface WorkOrderSummary {
  id: number
  code: string
  type: number
  typeDesc: string
  title: string
  submitterInfo?: HandlerInfo
  auditorInfo?: HandlerInfo[]
  distributerInfo?: HandlerInfo
  handlerInfo?: HandlerInfo[]
  checkerInfo?: HandlerInfo
  priorityLevel: number
  priorityLevelDesc: string
  status: number
  statusDesc: string
  createTime: string
  updateTime?: string
  cancelTime?: string
  deleteTime?: string
  deadlineTime?: string
}

export interface AllowedAction {
  type: string
  label: string
  requireAssignedUser?: boolean
  requireRemark?: boolean
  dangerous?: boolean
}

export interface WorkOrderDetail extends WorkOrderSummary {
  content: string
  accessoryUrl?: string
  accessoryName?: string
  allowedActions?: AllowedAction[]
}

export interface WorkOrderPageParams {
  pageNum: number
  pageSize: number
  id?: number
  code?: string
  type?: number
  title?: string
  content?: string
  priorityLevel?: number
  status?: number[]
  createTimeFrom?: number
  createTimeTo?: number
  deadLineFrom?: number
  deadLineTo?: number
  submitterInfo?: Record<string, unknown>
  auditorInfo?: Record<string, unknown>
  distributerInfo?: Record<string, unknown>
  handlerInfo?: Record<string, unknown>
  checkerInfo?: Record<string, unknown>
}

export interface WorkOrderCreateParams {
  type: number
  title: string
  content: string
  priorityLevel: number
  flowId: number
  deadlineTime: number
}

export interface DashboardData {
  monthFinishedNum: number
  unHandledNum: number
  unAuditedNum: number
  delayNum: number
}

export interface StatisticItem {
  status?: number
  statusDesc?: string
  type?: number
  typeDesc?: string
  quantity: number
}

export interface WeekHandleItem {
  date: string
  dailyTotalNum: number
  dailyFinishedNum: number
}

export interface WorkOrderTodo {
  id?: number
  code?: string
  title: string
  type: number
  typeDesc: string
  status: number
  statusDesc: string
  createTime: string
}

export interface MessageItem {
  id?: number
  workOrderId?: number
  workOrderCode?: string
  content: string
  type: number
  typeDesc: string
  sendTime: string
  read?: boolean
}

export interface FlowNode {
  id?: number
  nodeType?: number
  nodeTypeDesc?: string
  handlerId: number
  handlerName: string
  isLastNode?: boolean
}

export interface FlowDefinition {
  flowId: string
  flowName: string
  nodes: FlowNode[]
}

export interface FlowPayload {
  flowId?: number
  flowName: string
  nodes: FlowNode[]
  distributeNode: FlowNode
  checkNode: FlowNode
}
