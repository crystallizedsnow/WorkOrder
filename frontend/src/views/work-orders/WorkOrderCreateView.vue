<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { flowApi } from '@/api/modules/flow'
import { workOrderApi } from '@/api/modules/work-order'
import type { FlowDefinition } from '@/api/types'
import { ORDER_TYPES, PRIORITIES } from '@/constants/work-order'

const router = useRouter()
const formRef = ref<FormInstance>()
const flows = ref<FlowDefinition[]>([])
const submitting = ref(false)
const form = reactive({ type: 1, title: '', content: '', priorityLevel: 1, flowId: undefined as number | undefined, deadline: undefined as Date | undefined })
const rules: FormRules = {
  title: [{ required: true, message: '请输入工单标题', trigger: 'blur' }, { max: 100, message: '标题不能超过 100 字', trigger: 'blur' }],
  content: [{ required: true, message: '请输入问题描述', trigger: 'blur' }],
  flowId: [{ required: true, message: '请选择流程', trigger: 'change' }],
  deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
}
onMounted(async () => { try { flows.value = (await flowApi.page()).records } catch (error) { ElMessage.error(error instanceof Error ? error.message : '流程加载失败') } })
async function submit() {
  if (!(await formRef.value?.validate().catch(() => false)) || !form.flowId || !form.deadline) return
  submitting.value = true
  try {
    await workOrderApi.create({ type: form.type, title: form.title.trim(), content: form.content.trim(), priorityLevel: form.priorityLevel, flowId: form.flowId, deadlineTime: Math.floor(form.deadline.getTime() / 1000) })
    ElMessage.success('工单创建成功'); await router.push('/work-orders')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') }
  finally { submitting.value = false }
}
</script>

<template>
  <div class="page create-page">
    <div class="page-header"><div><h1 class="page-title">新建工单</h1><p class="page-description">描述问题并选择合适的处理流程</p></div><el-button @click="router.back()">返回</el-button></div>
    <section class="surface">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid"><el-form-item label="工单类型" prop="type"><el-radio-group v-model="form.type"><el-radio-button v-for="item in ORDER_TYPES" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button></el-radio-group></el-form-item><el-form-item label="优先级" prop="priorityLevel"><el-radio-group v-model="form.priorityLevel"><el-radio-button v-for="item in PRIORITIES" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button></el-radio-group></el-form-item></div>
        <el-form-item label="工单标题" prop="title"><el-input v-model="form.title" maxlength="100" show-word-limit placeholder="用一句话概括问题" /></el-form-item>
        <el-form-item label="详细描述" prop="content"><el-input v-model="form.content" type="textarea" :rows="8" maxlength="3000" show-word-limit placeholder="请提供背景、现象、影响范围和期望结果" /></el-form-item>
        <div class="form-grid"><el-form-item label="处理流程" prop="flowId"><el-select v-model="form.flowId" filterable placeholder="请选择流程" style="width: 100%"><el-option v-for="item in flows" :key="item.flowId" :label="item.flowName" :value="Number(item.flowId)" /></el-select></el-form-item><el-form-item label="截止时间" prop="deadline"><el-date-picker v-model="form.deadline" type="datetime" placeholder="请选择截止时间" :disabled-date="(date: Date) => date.getTime() < Date.now() - 86400000" style="width: 100%" /></el-form-item></div>
        <el-alert title="一期暂不支持上传附件" description="历史工单中已有的附件仍可在详情页查看。" type="info" :closable="false" show-icon />
        <div class="actions"><el-button @click="router.back()">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">提交工单</el-button></div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>.create-page { max-width: 920px; margin: 0 auto; }.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 24px; }</style>
