<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { organizationApi } from '@/api/modules/organization'
import type { Staff } from '@/api/types'

const loading = ref(false); const rows = ref<Staff[]>([]); const total = ref(0); const page = reactive({ pageNum: 1, pageSize: 10 }); const dialog = reactive({ open: false, edit: false }); const form = reactive({ id: undefined as number | undefined, name: '', companyName: '', departmentName: '', position: '', managerName: '', phone: '', email: '', status: 0 })
async function load() { loading.value = true; try { const result = await organizationApi.adminStaff(page); rows.value = result.records; total.value = result.total } catch (error) { ElMessage.error(error instanceof Error ? error.message : '人员加载失败') } finally { loading.value = false } }
function open(item?: Staff) { dialog.open = true; dialog.edit = Boolean(item); Object.assign(form, item ? { id: item.id, name: item.name, companyName: item.company, departmentName: item.department, position: item.position, managerName: item.managerName ?? '', phone: item.phone, email: item.email, status: item.status } : { id: undefined, name: '', companyName: '', departmentName: '', position: '', managerName: '', phone: '', email: '', status: 0 }) }
async function save() { try { if (dialog.edit) await organizationApi.updateStaff({ id: form.id, companyName: form.companyName, departmentName: form.departmentName, position: form.position, status: form.status }); else await organizationApi.addStaff(form); ElMessage.success('保存成功'); dialog.open = false; await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } }
async function remove(item: Staff) { try { await ElMessageBox.confirm(`确定删除员工 ${item.name} 吗？`, '请确认', { type: 'warning' }); await organizationApi.deleteStaff(item.id); await load() } catch (error) { if (error instanceof Error) ElMessage.error(error.message) } }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h1 class="page-title">人员管理</h1><p class="page-description">管理员次优先功能，不作为本期验收门槛</p></div><el-button type="primary" :icon="Plus" @click="open()">新增员工</el-button></div><section class="surface"><el-table v-loading="loading" :data="rows"><el-table-column prop="staffNumber" label="工号" width="170" /><el-table-column prop="name" label="姓名" width="100" /><el-table-column prop="company" label="公司" /><el-table-column prop="department" label="部门" /><el-table-column prop="position" label="职位" /><el-table-column prop="status" label="状态" width="90"><template #default="{ row }">{{ ['正常','休假','停职','离职'][row.status] }}</template></el-table-column><el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table><div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" layout="total, prev, pager, next" :total="total" @change="load" /></div></section>
    <el-dialog v-model="dialog.open" :title="dialog.edit ? '编辑员工' : '新增员工'" width="560px"><el-form :model="form" label-position="top"><div class="form-grid"><el-form-item label="姓名"><el-input v-model="form.name" :disabled="dialog.edit" /></el-form-item><el-form-item label="职位"><el-input v-model="form.position" /></el-form-item><el-form-item label="公司"><el-input v-model="form.companyName" /></el-form-item><el-form-item label="部门"><el-input v-model="form.departmentName" /></el-form-item><el-form-item label="手机号"><el-input v-model="form.phone" :disabled="dialog.edit" /></el-form-item><el-form-item label="邮箱"><el-input v-model="form.email" :disabled="dialog.edit" /></el-form-item><el-form-item v-if="!dialog.edit" label="直属领导"><el-input v-model="form.managerName" /></el-form-item><el-form-item v-if="dialog.edit" label="状态"><el-select v-model="form.status"><el-option label="正常" :value="0" /><el-option label="休假" :value="1" /><el-option label="停职" :value="2" /><el-option label="离职" :value="3" /></el-select></el-form-item></div></el-form><template #footer><el-button @click="dialog.open=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>.pagination { display:flex; justify-content:flex-end; margin-top:18px; }.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 16px; }</style>
