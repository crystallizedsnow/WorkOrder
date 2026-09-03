<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, ChatDotRound, DataAnalysis, Document, OfficeBuilding, Setting, Tickets, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import AssistantDrawer from '@/views/assistant/AssistantDrawer.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const assistantOpen = ref(false)
const active = computed(() => route.path)

const menus = computed(() => [
  { path: '/dashboard', label: '工作台', icon: DataAnalysis },
  { path: '/work-orders', label: '工单中心', icon: Tickets },
  { path: '/flows', label: '流程管理', icon: Document },
  { path: '/messages', label: '消息中心', icon: Bell },
  { path: '/organization', label: '组织架构', icon: OfficeBuilding },
  { path: '/assistant', label: 'AI 助手', icon: ChatDotRound },
  { path: '/profile', label: '个人中心', icon: User },
  ...(auth.isAdmin ? [{ path: '/admin/staff', label: '人员管理', icon: Setting }, { path: '/admin/organization', label: '组织管理', icon: OfficeBuilding }] : []),
])

async function logout() { await auth.logout(); await router.replace('/login') }
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand__mark">W</span><span>智能工单中心</span></div>
      <el-menu :default-active="active" router class="nav">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <button class="assistant-button" type="button" @click="assistantOpen = true">
        <el-icon><ChatDotRound /></el-icon><span>随时问 Agent</span>
      </button>
    </aside>
    <div class="main">
      <header class="topbar">
        <div class="topbar__context"><span class="dot" />系统服务台</div>
        <el-dropdown trigger="click">
          <div class="user"><el-avatar :size="32">{{ auth.profile?.name?.slice(0, 1) }}</el-avatar><span>{{ auth.profile?.name ?? '用户' }}</span></div>
          <template #dropdown><el-dropdown-menu><el-dropdown-item @click="router.push('/profile')">个人资料</el-dropdown-item><el-dropdown-item divided @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </header>
      <main class="content"><RouterView /></main>
    </div>
    <AssistantDrawer v-model="assistantOpen" />
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100vh; }
.sidebar { position: fixed; inset: 0 auto 0 0; z-index: 10; display: flex; flex-direction: column; width: 228px; padding: 18px 14px; background: #fff; border-right: 1px solid var(--wo-border); }
.brand { display: flex; align-items: center; gap: 11px; height: 48px; padding: 0 10px; font-size: 16px; font-weight: 700; }
.brand__mark { display: grid; width: 30px; height: 30px; color: #fff; background: linear-gradient(135deg, #315efb, #6b8cff); border-radius: 9px; place-items: center; }
.nav { flex: 1; margin-top: 18px; border-right: 0; }
.nav :deep(.el-menu-item) { height: 46px; margin-bottom: 5px; border-radius: 9px; }
.nav :deep(.el-menu-item.is-active) { background: var(--wo-primary-soft); }
.assistant-button { display: flex; align-items: center; justify-content: center; gap: 8px; height: 44px; color: #fff; cursor: pointer; background: var(--wo-primary); border: 0; border-radius: 10px; }
.main { width: calc(100% - 228px); margin-left: 228px; }
.topbar { position: sticky; top: 0; z-index: 9; display: flex; align-items: center; justify-content: space-between; height: 64px; padding: 0 28px; background: rgba(255,255,255,.92); border-bottom: 1px solid var(--wo-border); backdrop-filter: blur(10px); }
.topbar__context, .user { display: flex; align-items: center; gap: 9px; }
.dot { width: 8px; height: 8px; background: var(--wo-success); border-radius: 50%; }
.user { cursor: pointer; }
.content { padding: 26px 28px 44px; }

@media (max-width: 1100px) {
  .sidebar { width: 76px; padding-inline: 10px; }
  .brand { justify-content: center; padding: 0; }
  .brand > span:last-child, .assistant-button span { display: none; }
  .nav :deep(.el-menu-item) { justify-content: center; padding: 0 !important; }
  .nav :deep(.el-menu-item span) { display: none; }
  .nav :deep(.el-icon) { margin: 0; }
  .main { width: calc(100% - 76px); margin-left: 76px; }
  .topbar { padding-inline: 20px; }
  .content { padding: 22px 20px 36px; }
}
</style>
