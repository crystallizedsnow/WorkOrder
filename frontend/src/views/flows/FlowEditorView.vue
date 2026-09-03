<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { flowApi } from '@/api/modules/flow'
import type { FlowNode, Staff } from '@/api/types'
import StaffPicker from '@/components/business/StaffPicker.vue'

const route = useRoute(); const router = useRouter(); const formRef = ref<FormInstance>(); const saving = ref(false)
const editing = computed(() => Boolean(route.params.id))
const form = reactive({ flowName: '', audits: [{ handlerId: undefined as number | undefined, handlerName: '' }], distribute: { handlerId: undefined as number | undefined, handlerName: '' }, check: { handlerId: undefined as number | undefined, handlerName: '' } })
const rules: FormRules = { flowName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }] }
function setStaff(target: { handlerId?: number; handlerName: string }, staff: Staff) { target.handlerId = staff.id; target.handlerName = staff.name }
function addAudit() { form.audits.push({ handlerId: undefined, handlerName: '' }) }
function removeAudit(index: number) { if (form.audits.length > 1) form.audits.splice(index, 1) }
onMounted(async () => {
  if (!editing.value) return
  try {
    const detail = await flowApi.detail(Number(route.params.id))
    form.flowName = detail.flowName
    const audits = detail.nodes.filter((n) => n.nodeType === 2); const distribute = detail.nodes.find((n) => n.nodeType === 3); const check = detail.nodes.find((n) => n.nodeType === 5)
    form.audits = audits.map((n) => ({ handlerId: n.handlerId, handlerName: n.handlerName }))
    if (distribute) Object.assign(form.distribute, distribute); if (check) Object.assign(form.check, check)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '流程详情加载失败') }
})
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (form.audits.some((n) => !n.handlerId) || !form.distribute.handlerId || !form.check.handlerId) return ElMessage.warning('请为所有节点选择处理人')
  const ids = form.audits.map((n) => n.handlerId); if (new Set(ids).size !== ids.length) return ElMessage.warning('审核人不能重复')
  const node = (value: { handlerId?: number; handlerName: string }): FlowNode => ({ handlerId: value.handlerId!, handlerName: value.handlerName })
  const payload = { ...(editing.value ? { flowId: Number(route.params.id) } : {}), flowName: form.flowName.trim(), nodes: form.audits.map(node), distributeNode: node(form.distribute), checkNode: node(form.check) }
  saving.value = true
  try { if (editing.value) await flowApi.update(payload); else await flowApi.create(payload); ElMessage.success('流程已保存'); await router.push('/flows') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
</script>

<template>
  <div class="page editor">
    <div class="page-header"><div><h1 class="page-title">{{ editing ? '编辑流程' : '新建流程' }}</h1><p class="page-description">按顺序配置审核、派单与验收节点</p></div><el-button @click="router.back()">返回</el-button></div>
    <section class="surface"><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="流程名称" prop="flowName"><el-input v-model="form.flowName" maxlength="50" placeholder="例如：线上故障处理流程" /></el-form-item></el-form></section>
    <section class="surface"><div class="toolbar"><div><h2>审核节点</h2><p class="muted">审核将按照下面的顺序依次进行</p></div><el-button :icon="Plus" @click="addAudit">增加审核节点</el-button></div><div class="audit-list"><div v-for="(node, index) in form.audits" :key="index" class="audit-row"><span class="sequence">{{ index + 1 }}</span><StaffPicker v-model="node.handlerId" placeholder="选择审核人" @select="(staff) => setStaff(node, staff)" /><el-button :icon="Delete" circle text :disabled="form.audits.length === 1" @click="removeAudit(index)" /></div></div></section>
    <section class="fixed-grid"><div class="surface"><div class="node-label">派单节点</div><p class="muted">审核完成后由该员工分派处理人</p><StaffPicker v-model="form.distribute.handlerId" placeholder="选择派单人" @select="(staff) => setStaff(form.distribute, staff)" /></div><div class="surface"><div class="node-label">验收节点</div><p class="muted">处理完成后由该员工确认结果</p><StaffPicker v-model="form.check.handlerId" placeholder="选择验收人" @select="(staff) => setStaff(form.check, staff)" /></div></section>
    <div class="actions"><el-button @click="router.back()">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存流程</el-button></div>
  </div>
</template>

<style scoped>.editor { max-width: 920px; margin: 0 auto; }.surface h2 { margin: 0; font-size: 17px; }.surface p { margin: 6px 0 18px; font-size: 13px; }.audit-list { display: flex; flex-direction: column; gap: 10px; margin-top: 18px; }.audit-row { display: grid; align-items: center; grid-template-columns: 32px 1fr 34px; gap: 10px; }.sequence { display: grid; width: 28px; height: 28px; color: var(--wo-primary); background: var(--wo-primary-soft); border-radius: 50%; place-items: center; }.fixed-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }.node-label { font-weight: 650; }.actions { display: flex; justify-content: flex-end; gap: 10px; }</style>
