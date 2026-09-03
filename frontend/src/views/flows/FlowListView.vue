<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { flowApi } from '@/api/modules/flow'
import type { FlowDefinition } from '@/api/types'

const router = useRouter(); const loading = ref(false); const flows = ref<FlowDefinition[]>([])
async function load() { loading.value = true; try { flows.value = (await flowApi.page(1, 100)).records } catch (error) { ElMessage.error(error instanceof Error ? error.message : '流程加载失败') } finally { loading.value = false } }
async function remove(item: FlowDefinition) { try { await ElMessageBox.confirm(`确定删除流程“${item.flowName}”吗？`, '删除流程', { type: 'warning' }); await flowApi.remove(Number(item.flowId)); ElMessage.success('已删除'); await load() } catch (error) { if (error instanceof Error) ElMessage.error(error.message) } }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h1 class="page-title">流程管理</h1><p class="page-description">所有登录用户都可以按照当前后端规则维护流程</p></div><el-button type="primary" :icon="Plus" @click="router.push('/flows/new')">新建流程</el-button></div>
    <section v-loading="loading" class="flow-grid">
      <article v-for="item in flows" :key="item.flowId" class="flow-card"><div class="flow-card__head"><div><span class="flow-id">#{{ item.flowId }}</span><h2>{{ item.flowName }}</h2></div><el-dropdown><el-button text>•••</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="router.push(`/flows/${item.flowId}/edit`)">编辑</el-dropdown-item><el-dropdown-item divided @click="remove(item)">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div><div class="nodes"><template v-for="(node, index) in item.nodes" :key="node.id"><div class="node"><span>{{ node.nodeTypeDesc }}</span><strong>{{ node.handlerName }}</strong></div><i v-if="index < item.nodes.length - 1">→</i></template></div><el-button link type="primary" @click="router.push(`/flows/${item.flowId}/edit`)">查看并编辑 →</el-button></article>
      <div v-if="!loading && !flows.length" class="surface empty-hint">暂无流程，请先新建流程</div>
    </section>
  </div>
</template>

<style scoped>.flow-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 16px; }.flow-card { padding: 20px; background: #fff; border: 1px solid var(--wo-border); border-radius: var(--wo-radius); }.flow-card__head { display: flex; justify-content: space-between; }.flow-card h2 { margin: 5px 0 18px; font-size: 18px; }.flow-id { color: var(--wo-text-secondary); font-size: 12px; }.nodes { display: flex; align-items: center; gap: 8px; min-height: 78px; padding: 12px; margin-bottom: 12px; overflow-x: auto; background: var(--wo-bg); border-radius: 9px; }.node { display: flex; flex: 0 0 auto; flex-direction: column; gap: 4px; min-width: 100px; padding: 9px 11px; background: #fff; border: 1px solid var(--wo-border); border-radius: 8px; }.node span { color: var(--wo-text-secondary); font-size: 11px; }.node strong { font-size: 13px; }</style>
