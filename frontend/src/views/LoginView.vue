<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Phone, Tickets } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })
const rules: FormRules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await auth.login(form.phone.trim(), form.password)
    ElMessage.success('登录成功')
    await router.replace(String(route.query.redirect || '/dashboard'))
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '登录失败') }
  finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="intro">
      <div class="intro__content">
        <div class="badge"><el-icon><Tickets /></el-icon> Work Order OS</div>
        <h1>让每一张工单<br />都有清晰的去向</h1>
        <p>统一管理提交、审批、派单、处理与验收，并让 AI Agent 帮你快速完成查询和操作。</p>
        <div class="flow"><span>创建</span><i /><span>审批</span><i /><span>处理</span><i /><span>验收</span></div>
      </div>
    </section>
    <section class="login-panel">
      <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="submit">
        <div class="mobile-logo"><el-icon><Tickets /></el-icon></div>
        <h2>欢迎回来</h2><p>登录智能工单中心</p>
        <el-form-item prop="phone"><el-input v-model="form.phone" size="large" placeholder="手机号" :prefix-icon="Phone" /></el-form-item>
        <el-form-item prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" :prefix-icon="Lock" /></el-form-item>
        <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="submit">登录</el-button>
        <div class="security-note">Refresh Token 使用 HttpOnly Cookie 保存</div>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page { display: grid; min-height: 100vh; grid-template-columns: 1.15fr .85fr; background: #fff; }
.intro { position: relative; display: grid; overflow: hidden; padding: 72px; color: #fff; background: linear-gradient(145deg, #14285d 0%, #315efb 58%, #7793ff 100%); place-items: center; }
.intro::after { position: absolute; width: 520px; height: 520px; content: ''; border: 1px solid rgba(255,255,255,.18); border-radius: 50%; transform: translate(36%, 40%); }
.intro__content { position: relative; z-index: 1; max-width: 580px; }
.badge { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; font-size: 13px; background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.2); border-radius: 999px; }
h1 { margin: 34px 0 20px; font-size: 52px; line-height: 1.16; letter-spacing: -.04em; }
.intro p { max-width: 520px; color: rgba(255,255,255,.78); font-size: 17px; line-height: 1.8; }
.flow { display: flex; align-items: center; margin-top: 48px; color: rgba(255,255,255,.9); }
.flow i { width: 46px; height: 1px; margin: 0 10px; background: rgba(255,255,255,.4); }
.login-panel { display: grid; padding: 48px; place-items: center; }
.login-form { width: 360px; }
.login-form h2 { margin: 18px 0 6px; font-size: 30px; }
.login-form > p { margin: 0 0 30px; color: var(--wo-text-secondary); }
.login-form :deep(.el-form-item) { margin-bottom: 20px; }
.mobile-logo { display: grid; width: 48px; height: 48px; color: #fff; font-size: 22px; background: var(--wo-primary); border-radius: 14px; place-items: center; }
.security-note { margin-top: 18px; color: var(--wo-text-secondary); font-size: 12px; text-align: center; }

@media (max-width: 900px) {
  .login-page { grid-template-columns: 1fr; }
  .intro { display: none; }
  .login-panel { padding: 36px; }
}
</style>
