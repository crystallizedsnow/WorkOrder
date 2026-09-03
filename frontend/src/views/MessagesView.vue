<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '@/api/modules/dashboard'
import type { MessageItem } from '@/api/types'

const router = useRouter(); const rows = ref<MessageItem[]>([]); const total = ref(0); const loading = ref(false); const page = reactive({ current: 1, size: 10 })
async function load() { loading.value = true; try { const result = await dashboardApi.messages(page.current, page.size); rows.value = result.records; total.value = result.total } catch (error) { ElMessage.error(error instanceof Error ? error.message : '消息加载失败') } finally { loading.value = false } }
function openMessage(item: MessageItem) { if (item.workOrderId) router.push(`/work-orders/${item.workOrderId}`); else ElMessage.info('后端尚未返回关联工单 ID') }
onMounted(load)
</script>

<template><div class="page"><div class="page-header"><div><h1 class="page-title">消息中心</h1><p class="page-description">查看工单流转通知；已读能力等待后端接口补充</p></div></div><section class="surface"><div v-loading="loading" class="message-list"><button v-for="item in rows" :key="item.id ?? `${item.sendTime}-${item.content}`" class="message-item" type="button" @click="openMessage(item)"><span class="message-icon" /><span class="message-body"><strong>{{ item.typeDesc }}</strong><span>{{ item.content }}</span><time>{{ item.sendTime }}</time></span></button><el-empty v-if="!loading && !rows.length" description="暂无消息" /></div><div class="pagination"><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div></section></div></template>

<style scoped>.message-list { display:flex; flex-direction:column; }.message-item { display:flex; gap:14px; width:100%; padding:16px 6px; color:inherit; text-align:left; cursor:pointer; background:none; border:0; border-bottom:1px solid var(--wo-border); }.message-item:hover { background:#fafbff; }.message-icon { flex:0 0 auto; width:9px; height:9px; margin-top:7px; background:var(--wo-primary); border-radius:50%; }.message-body { display:flex; flex:1; flex-direction:column; gap:6px; }.message-body > span { color:var(--wo-text-secondary); }.message-body time { color:#98a0b3; font-size:12px; }.pagination { display:flex; justify-content:flex-end; margin-top:18px; }</style>
