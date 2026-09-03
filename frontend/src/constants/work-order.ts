export const ORDER_TYPES = [
  { value: 0, label: '需求' },
  { value: 1, label: '故障' },
] as const

export const PRIORITIES = [
  { value: 0, label: '高' },
  { value: 1, label: '中' },
  { value: 2, label: '低' },
] as const

export const ORDER_STATUSES = [
  { value: 100, label: '未审核' },
  { value: 200, label: '审核中' },
  { value: 270, label: '审核失败' },
  { value: 300, label: '未派单' },
  { value: 400, label: '处理中' },
  { value: 410, label: '已超时' },
  { value: 500, label: '已处理' },
  { value: 600, label: '已验收' },
  { value: 670, label: '验收失败' },
  { value: 700, label: '已取消' },
] as const

export const HANDLE_ACTIONS: Record<string, number> = {
  distribute: 1,
  apply_help: 2,
  urge: 3,
  finish: 4,
  check_success: 5,
  check_failure: 6,
}
