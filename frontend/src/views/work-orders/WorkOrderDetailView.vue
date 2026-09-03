<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Printer } from '@element-plus/icons-vue'
import { workOrderApi } from '@/api/modules/work-order'
import type { AllowedAction, HandlerInfo, WorkOrderDetail } from '@/api/types'
import { HANDLE_ACTIONS } from '@/constants/work-order'
import StatusTag from '@/components/business/StatusTag.vue'
import StaffPicker from '@/components/business/StaffPicker.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute(); const router = useRouter()
const auth = useAuthStore()
const detail = ref<WorkOrderDetail>(); const loading = ref(true); const submitting = ref(false)
const dialog = reactive({ open: false, action: null as AllowedAction | null, assignedUserId: undefined as number | undefined, remark: '' })
const fallbackActions = computed<AllowedAction[]>(() => {
  const status = detail.value?.status
  if ([100, 200].includes(status ?? -1)) return [{ type: 'approve', label: '通过审核', requireRemark: true }, { type: 'reject', label: '驳回', requireRemark: true, dangerous: true }]
  if (status === 300) return [{ type: 'distribute', label: '派单', requireAssignedUser: true, requireRemark: true }]
  if ([400, 410].includes(status ?? -1)) return [{ type: 'apply_help', label: '请求协助', requireAssignedUser: true, requireRemark: true }, { type: 'urge', label: '催单', requireRemark: true }, { type: 'finish', label: '完成处理', requireRemark: true }]
  if (status === 500) return [{ type: 'check_success', label: '确认完成', requireRemark: true }, { type: 'check_failure', label: '仍有问题', requireRemark: true, dangerous: true }]
  return []
})
const actions = computed(() => {
  const values = detail.value?.allowedActions !== undefined ? detail.value.allowedActions : fallbackActions.value
  return values.filter((item) => !['cancel', 'delete'].includes(item.type))
})
const mayCancel = computed(() => detail.value?.allowedActions?.some((item) => item.type === 'cancel') ?? Boolean(detail.value && detail.value.status < 500 && String(detail.value.submitterInfo?.userId) === auth.profile?.userId))
const mayDelete = computed(() => detail.value?.allowedActions?.some((item) => item.type === 'delete') ?? Boolean(detail.value && detail.value.status < 500))
const timeline = computed(() => {
  if (!detail.value) return []
  return [detail.value.submitterInfo, ...(detail.value.auditorInfo ?? []), detail.value.distributerInfo, ...(detail.value.handlerInfo ?? []), detail.value.checkerInfo].filter(Boolean) as HandlerInfo[]
})

async function load() { loading.value = true; try { detail.value = await workOrderApi.detail(Number(route.params.id)) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '详情加载失败') } finally { loading.value = false } }
function openAction(action: AllowedAction) { Object.assign(dialog, { open: true, action, assignedUserId: undefined, remark: '' }) }
async function submitAction() {
  if (!detail.value || !dialog.action) return
  if (dialog.action.requireAssignedUser && !dialog.assignedUserId) return ElMessage.warning('请选择员工')
  if (dialog.action.requireRemark && !dialog.remark.trim()) return ElMessage.warning('请填写操作备注')
  submitting.value = true
  try {
    if (dialog.action.type === 'approve' || dialog.action.type === 'reject') await workOrderApi.approve({ id: detail.value.id, isApproved: dialog.action.type === 'approve', remark: dialog.remark })
    else await workOrderApi.handle({ id: detail.value.id, handleType: HANDLE_ACTIONS[dialog.action.type]!, assignedUserId: dialog.assignedUserId, remark: dialog.remark })
    ElMessage.success('操作成功'); dialog.open = false; await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '操作失败') }
  finally { submitting.value = false }
}
async function destructive(kind: 'cancel' | 'delete') {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(`确定要${kind === 'cancel' ? '取消' : '删除'}工单 ${detail.value.code} 吗？`, '请确认', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '返回' })
    if (kind === 'cancel') await workOrderApi.cancel(detail.value.id); else await workOrderApi.remove(detail.value.id)
    ElMessage.success('操作成功'); await router.push('/work-orders')
  } catch (error) { if (error instanceof Error) ElMessage.error(error.message) }
}
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header"><div><el-button link @click="router.push('/work-orders')">← 返回工单列表</el-button><h1 class="page-title detail-title">{{ detail?.title || '工单详情' }}</h1><p class="page-description">{{ detail?.code }}</p></div><div class="toolbar__group"><el-button :icon="Printer" @click="detail && workOrderApi.download('print', { id: detail.id })">打印</el-button><el-button v-for="action in actions" :key="action.type" :type="action.dangerous ? 'danger' : 'primary'" :plain="action.dangerous" @click="openAction(action)">{{ action.label }}</el-button></div></div>
    <template v-if="detail">
      <section class="surface summary"><div><span>状态</span><StatusTag :status="detail.status" :label="detail.statusDesc" /></div><div><span>类型</span><strong>{{ detail.typeDesc }}</strong></div><div><span>优先级</span><strong>{{ detail.priorityLevelDesc }}</strong></div><div><span>创建时间</span><strong>{{ detail.createTime }}</strong></div><div><span>截止时间</span><strong>{{ detail.deadlineTime || '-' }}</strong></div></section>
      <div class="detail-grid">
        <section class="surface"><h2>问题描述</h2><div class="content-text">{{ detail.content }}</div><div v-if="detail.accessoryUrl" class="attachment"><span>附件</span><a :href="detail.accessoryUrl" target="_blank" rel="noopener noreferrer">{{ detail.accessoryName || '查看附件' }}</a></div></section>
        <section class="surface"><h2>处理轨迹</h2><el-timeline><el-timeline-item v-for="item in timeline" :key="item.id" :timestamp="item.handleTime || item.createTime" placement="top"><div class="timeline-title">{{ item.handleTypeDesc }} · {{ item.userName }}</div><div class="muted">{{ item.departmentName }}<span v-if="item.finishedDesc"> · {{ item.finishedDesc }}</span></div><p v-if="item.remark">{{ item.remark }}</p></el-timeline-item></el-timeline></section>
      </div>
      <section v-if="mayCancel || mayDelete" class="danger-zone"><div><strong>其他操作</strong><div class="muted">取消仅限提交人，删除与取消均受后端状态校验。</div></div><div><el-button v-if="mayCancel" @click="destructive('cancel')">取消工单</el-button><el-button v-if="mayDelete" type="danger" plain @click="destructive('delete')">删除工单</el-button></div></section>
    </template>
    <el-dialog v-model="dialog.open" :title="dialog.action?.label" width="480px">
      <el-form label-position="top"><el-form-item v-if="dialog.action?.requireAssignedUser" label="选择员工" required><StaffPicker v-model="dialog.assignedUserId" /></el-form-item><el-form-item label="操作备注" :required="dialog.action?.requireRemark"><el-input v-model="dialog.remark" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="dialog.open = false">取消</el-button><el-button :type="dialog.action?.dangerous ? 'danger' : 'primary'" :loading="submitting" @click="submitAction">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-title { margin-top: 12px; }.summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); }.summary > div { display: flex; flex-direction: column; gap: 9px; }.summary span { color: var(--wo-text-secondary); font-size: 13px; }.detail-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(360px, .65fr); gap: 16px; }.surface h2 { margin: 0 0 18px; font-size: 17px; }.content-text { min-height: 180px; line-height: 1.8; white-space: pre-wrap; }.attachment { display: flex; gap: 18px; padding-top: 16px; margin-top: 20px; border-top: 1px solid var(--wo-border); }.attachment a { color: var(--wo-primary); }.timeline-title { font-weight: 650; }.danger-zone { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; background: #fffafa; border: 1px solid #f5d7d9; border-radius: var(--wo-radius); }.danger-zone .muted { margin-top: 5px; font-size: 13px; }
</style>
