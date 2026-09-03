<script setup lang="ts">
import { reactive, ref, watchEffect } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/modules/auth'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore(); const saving = ref(false); const form = reactive({ phone: '', email: '', password: '' })
watchEffect(() => { if (auth.profile) { form.phone = auth.profile.phone; form.email = auth.profile.email } })
async function save() { saving.value = true; try { await authApi.updateProfile({ phone: form.phone, email: form.email, ...(form.password ? { password: form.password } : {}) }); await auth.loadProfile(); form.password = ''; ElMessage.success('个人资料已更新') } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false } }
</script>

<template>
  <div class="page profile-page"><div class="page-header"><div><h1 class="page-title">个人中心</h1><p class="page-description">查看身份和更新联系信息</p></div></div><section class="surface identity"><el-avatar :size="64">{{ auth.profile?.name?.slice(0,1) }}</el-avatar><div><h2>{{ auth.profile?.name }}</h2><p>{{ auth.profile?.staffNumber }} · {{ auth.profile?.position }}</p></div><el-tag>{{ auth.isAdmin ? '管理员' : '普通用户' }}</el-tag></section><section class="surface"><el-descriptions title="组织信息" :column="2" border><el-descriptions-item label="公司">{{ auth.profile?.companyName }}</el-descriptions-item><el-descriptions-item label="部门">{{ auth.profile?.departmentName }}</el-descriptions-item><el-descriptions-item label="直属领导">{{ auth.profile?.managerName || '-' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ auth.profile?.createTime }}</el-descriptions-item></el-descriptions></section><section class="surface"><h2>联系与安全</h2><el-form label-position="top" style="max-width: 560px"><el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item><el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item><el-form-item label="新密码"><el-input v-model="form.password" type="password" show-password placeholder="不修改请留空" /></el-form-item><el-button type="primary" :loading="saving" @click="save">保存修改</el-button></el-form></section></div>
</template>

<style scoped>.profile-page { max-width: 900px; }.identity { display: flex; align-items: center; gap: 18px; }.identity h2 { margin: 0 0 6px; }.identity p { margin: 0; color: var(--wo-text-secondary); }.identity .el-tag { margin-left: auto; }.surface > h2 { margin: 0 0 20px; font-size: 17px; }</style>
