<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '@/api/modules/dashboard'
import type { DashboardData, StatisticItem, WeekHandleItem, WorkOrderTodo } from '@/api/types'
import EChart from '@/components/common/EChart.vue'
import StatusTag from '@/components/business/StatusTag.vue'

const router = useRouter()
const loading = ref(true)
const data = ref<DashboardData>({ monthFinishedNum: 0, unHandledNum: 0, unAuditedNum: 0, delayNum: 0 })
const status = ref<StatisticItem[]>([])
const types = ref<StatisticItem[]>([])
const week = ref<WeekHandleItem[]>([])
const todos = ref<WorkOrderTodo[]>([])
const statusOption = computed(() => ({ tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['45%', '68%'], data: status.value.map((i) => ({ name: i.statusDesc, value: i.quantity })), label: { formatter: '{b}\n{c}' } }] }))
const weekOption = computed(() => ({ tooltip: { trigger: 'axis' }, legend: { data: ['总量', '已完成'] }, grid: { left: 40, right: 18, top: 35, bottom: 28 }, xAxis: { type: 'category', data: week.value.map((i) => i.date) }, yAxis: { type: 'value', minInterval: 1 }, series: [{ name: '总量', type: 'bar', data: week.value.map((i) => i.dailyTotalNum), itemStyle: { color: '#cbd6ff' } }, { name: '已完成', type: 'line', smooth: true, data: week.value.map((i) => i.dailyFinishedNum), itemStyle: { color: '#315efb' } }] }))

onMounted(async () => {
  try {
    const [overview, statusData, typeData, weekData, todoData] = await Promise.all([dashboardApi.data(), dashboardApi.status(), dashboardApi.type(), dashboardApi.week(), dashboardApi.todo()])
    data.value = overview; status.value = statusData; types.value = typeData; week.value = weekData; todos.value = todoData
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '看板加载失败') }
  finally { loading.value = false }
})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header"><div><h1 class="page-title">工作台</h1><p class="page-description">掌握工单运行情况和当前待办</p></div><el-button type="primary" @click="router.push('/work-orders/new')">新建工单</el-button></div>
    <section class="metric-grid">
      <article class="metric-card"><div class="metric-card__label">本月已处理</div><div class="metric-card__value">{{ data.monthFinishedNum }}</div></article>
      <article class="metric-card"><div class="metric-card__label">待处理</div><div class="metric-card__value">{{ data.unHandledNum }}</div></article>
      <article class="metric-card"><div class="metric-card__label">未审核</div><div class="metric-card__value">{{ data.unAuditedNum }}</div></article>
      <article class="metric-card"><div class="metric-card__label">已超时</div><div class="metric-card__value danger">{{ data.delayNum }}</div></article>
    </section>
    <section class="chart-grid">
      <div class="surface"><div class="section-title">本周处理趋势</div><EChart :option="weekOption" /></div>
      <div class="surface"><div class="section-title">工单状态分布</div><EChart :option="statusOption" /></div>
    </section>
    <section class="surface">
      <div class="toolbar"><div class="section-title">我的待办</div><el-button link type="primary" @click="router.push('/work-orders')">查看全部</el-button></div>
      <el-table :data="todos" style="margin-top: 12px" @row-click="(row: WorkOrderTodo) => row.id && router.push(`/work-orders/${row.id}`)">
        <el-table-column prop="code" label="编号" width="160"><template #default="{ row }">{{ row.code || '接口待补充' }}</template></el-table-column>
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column prop="typeDesc" label="类型" width="100" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" :label="row.statusDesc" /></template></el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>.danger { color: var(--wo-danger); }.section-title { font-size: 16px; font-weight: 650; }</style>
