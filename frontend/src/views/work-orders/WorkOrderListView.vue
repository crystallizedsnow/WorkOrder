<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Plus, Search } from '@element-plus/icons-vue'
import { workOrderApi } from '@/api/modules/work-order'
import type { WorkOrderPageParams, WorkOrderSummary } from '@/api/types'
import { ORDER_STATUSES, ORDER_TYPES, PRIORITIES } from '@/constants/work-order'
import StatusTag from '@/components/business/StatusTag.vue'

const router = useRouter()
const loading = ref(false)
const rows = ref<WorkOrderSummary[]>([])
const total = ref(0)
const keyword = ref('')
const dateRange = ref<[Date, Date]>()
const filters = reactive<WorkOrderPageParams>({ pageNum: 1, pageSize: 10, status: [] })

async function load() {
  loading.value = true
  try {
    if (keyword.value.trim()) {
      const result = await workOrderApi.search(keyword.value.trim(), filters.pageNum, filters.pageSize)
      rows.value = result.list; total.value = result.total
    } else {
      const payload = { ...filters }
      if (dateRange.value) { payload.createTimeFrom = Math.floor(dateRange.value[0].getTime() / 1000); payload.createTimeTo = Math.floor(dateRange.value[1].getTime() / 1000) }
      const result = await workOrderApi.page(payload)
      rows.value = result.records; total.value = result.total
    }
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '工单加载失败') }
  finally { loading.value = false }
}
function reset() { keyword.value = ''; dateRange.value = undefined; Object.assign(filters, { pageNum: 1, pageSize: 10, type: undefined, priorityLevel: undefined, status: [] }); load() }
async function exportOrders() { try { await workOrderApi.download('export', filters); ElMessage.success('导出任务已完成') } catch (error) { ElMessage.error(error instanceof Error ? error.message : '导出失败') } }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h1 class="page-title">工单中心</h1><p class="page-description">查询、跟踪并处理所有工单</p></div><el-button type="primary" :icon="Plus" @click="router.push('/work-orders/new')">新建工单</el-button></div>
    <section class="surface filters">
      <div class="search-row"><el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="按标题、内容全文搜索" @keyup.enter="filters.pageNum = 1; load()" /><el-button type="primary" @click="filters.pageNum = 1; load()">搜索</el-button></div>
      <el-form inline label-position="top">
        <el-form-item label="类型"><el-select v-model="filters.type" clearable placeholder="全部类型" style="width: 130px"><el-option v-for="item in ORDER_TYPES" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-select v-model="filters.priorityLevel" clearable placeholder="全部优先级" style="width: 140px"><el-option v-for="item in PRIORITIES" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" multiple collapse-tags clearable placeholder="全部状态" style="width: 240px"><el-option v-for="item in ORDER_STATUSES" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="创建时间"><el-date-picker v-model="dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
        <el-form-item label=" "><el-button @click="reset">重置</el-button><el-button type="primary" @click="filters.pageNum = 1; load()">筛选</el-button></el-form-item>
      </el-form>
    </section>
    <section class="surface">
      <div class="toolbar"><span class="muted">共 {{ total }} 条工单</span><el-button :icon="Download" @click="exportOrders">导出当前筛选</el-button></div>
      <el-table v-loading="loading" :data="rows" style="margin-top: 14px" @row-click="(row: WorkOrderSummary) => router.push(`/work-orders/${row.id}`)">
        <el-table-column prop="code" label="编号" width="165" />
        <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
        <el-table-column prop="typeDesc" label="类型" width="90" />
        <el-table-column label="优先级" width="90"><template #default="{ row }"><span :class="`priority p${row.priorityLevel}`">{{ row.priorityLevelDesc }}</span></template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" :label="row.statusDesc" /></template></el-table-column>
        <el-table-column label="提交人" width="110"><template #default="{ row }">{{ row.submitterInfo?.userName || '-' }}</template></el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="175" />
        <el-table-column prop="deadlineTime" label="截止时间" width="175" />
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="filters.pageNum" v-model:page-size="filters.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div>
    </section>
  </div>
</template>

<style scoped>
.filters { padding-bottom: 4px; }.search-row { display: grid; grid-template-columns: minmax(360px, 600px) auto; gap: 10px; justify-content: start; margin-bottom: 14px; }.filters :deep(.el-form-item) { margin-bottom: 14px; }.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }.priority { font-weight: 650; }.p0 { color: var(--wo-danger); }.p1 { color: var(--wo-warning); }.p2 { color: var(--wo-success); }
</style>
