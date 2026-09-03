<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Promotion, VideoPause } from '@element-plus/icons-vue'
import { clearAssistantMemory, streamAssistant, type AssistantEvent } from '@/api/assistant-stream'
import MarkdownContent from '@/components/common/MarkdownContent.vue'

interface ChatMessage { id: string; role: 'user' | 'assistant' | 'status'; content: string; loading?: boolean }
const messages = ref<ChatMessage[]>([{ id: 'welcome', role: 'assistant', content: '你好，我是工单 Agent。你可以让我查询工单、分析待办，或在确认预演后执行工单操作。' }])
const input = ref(''); const running = ref(false); const scrollRoot = ref<HTMLDivElement>(); let controller: AbortController | null = null
const memoryId = ref(Number(sessionStorage.getItem('wo-agent-memory-id')) || createMemoryId())
function createMemoryId() { const value = Date.now() * 1000 + Math.floor(Math.random() * 1000); sessionStorage.setItem('wo-agent-memory-id', String(value)); return value }
async function scrollBottom() { await nextTick(); if (scrollRoot.value) scrollRoot.value.scrollTop = scrollRoot.value.scrollHeight }
function eventText(event: AssistantEvent) {
  if (typeof event.data === 'string') return event.data
  const data = event.data as Record<string, unknown>
  if (typeof data?.content === 'string') return data.content
  if (typeof data?.message === 'string') return data.message
  return ''
}
async function send() {
  const value = input.value.trim(); if (!value || running.value) return
  messages.value.push({ id: crypto.randomUUID(), role: 'user', content: value }); input.value = ''; running.value = true
  const answer: ChatMessage = { id: crypto.randomUUID(), role: 'assistant', content: '', loading: true }; messages.value.push(answer); controller = new AbortController(); await scrollBottom()
  try {
    await streamAssistant(memoryId.value, value, {
      onEvent(event) {
        if (event.event === 'error') throw new Error(eventText(event) || 'Agent 执行失败')
        if (event.event === 'tool.status') { answer.loading = true }
        else { const text = eventText(event); if (text) answer.content = event.event === 'message.delta' ? answer.content + text : text }
        scrollBottom()
      },
      onDone() { answer.loading = false },
    }, controller.signal)
  } catch (error) {
    answer.loading = false
    if ((error as Error).name === 'AbortError') answer.content ||= '已停止本次回答。'
    else { answer.content = `请求失败：${error instanceof Error ? error.message : '未知错误'}`; ElMessage.error(answer.content) }
  } finally { running.value = false; controller = null; await scrollBottom() }
}
function stop() { controller?.abort() }
async function clear() {
  try { await ElMessageBox.confirm('这会同时清除 Agent 服务中的当前会话记忆，是否继续？', '清空会话'); await clearAssistantMemory(memoryId.value); sessionStorage.removeItem('wo-agent-memory-id'); memoryId.value = createMemoryId(); messages.value = [{ id: 'welcome', role: 'assistant', content: '会话已清空。我们重新开始吧。' }] } catch (error) { if (error instanceof Error) ElMessage.error(error.message) }
}
onBeforeUnmount(stop)
</script>

<template>
  <div class="chat"><div class="chat__toolbar"><div><strong>工单 Agent</strong><span>会话 {{ memoryId }}</span></div><el-button :icon="Delete" text @click="clear">清空</el-button></div><div ref="scrollRoot" class="messages"><div v-for="message in messages" :key="message.id" :class="['message', `message--${message.role}`]"><div class="message__label">{{ message.role === 'user' ? '你' : message.role === 'assistant' ? 'Agent' : '状态' }}</div><div class="bubble"><MarkdownContent :content="message.content || '正在思考…'" /><span v-if="message.loading" class="typing">•••</span></div></div></div><div class="composer"><el-input v-model="input" type="textarea" resize="none" :rows="3" maxlength="2000" placeholder="例如：帮我查询正在处理的故障工单" @keydown.enter.exact.prevent="send" /><div class="composer__footer"><span>Enter 发送，Shift + Enter 换行</span><el-button v-if="running" type="danger" plain :icon="VideoPause" @click="stop">停止</el-button><el-button v-else type="primary" :icon="Promotion" :disabled="!input.trim()" @click="send">发送</el-button></div></div></div>
</template>

<style scoped>
.chat { display:flex; flex-direction:column; height:100%; min-height:0; background:#fff; }.chat__toolbar { display:flex; align-items:center; justify-content:space-between; padding:15px 18px; border-bottom:1px solid var(--wo-border); }.chat__toolbar strong { display:block; }.chat__toolbar span { display:block; margin-top:3px; color:var(--wo-text-secondary); font-size:11px; }.messages { flex:1; padding:22px 18px; overflow:auto; background:#f8f9fc; }.message { display:flex; flex-direction:column; align-items:flex-start; margin-bottom:18px; }.message--user { align-items:flex-end; }.message__label { margin:0 7px 5px; color:var(--wo-text-secondary); font-size:11px; }.bubble { max-width:82%; padding:12px 14px; background:#fff; border:1px solid var(--wo-border); border-radius:4px 14px 14px; box-shadow:0 3px 12px rgba(20,35,70,.04); }.message--user .bubble { color:#fff; background:var(--wo-primary); border-color:var(--wo-primary); border-radius:14px 4px 14px 14px; }.typing { display:inline-block; margin-top:6px; color:var(--wo-primary); letter-spacing:2px; animation:pulse 1s infinite; }.composer { padding:14px 16px; border-top:1px solid var(--wo-border); }.composer__footer { display:flex; align-items:center; justify-content:space-between; margin-top:8px; color:var(--wo-text-secondary); font-size:11px; }@keyframes pulse { 50% { opacity:.35; } }
</style>
