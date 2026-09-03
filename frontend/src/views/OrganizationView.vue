<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { organizationApi, type OrganizationNode } from '@/api/modules/organization'
import type { Staff } from '@/api/types'

const tree = ref<OrganizationNode[]>([]); const staff = ref<Staff[]>([]); const loading = ref(true); const keyword = ref('')
onMounted(async () => { try { const [treeData, staffData] = await Promise.all([organizationApi.tree(), organizationApi.staff({ pageNum: 1, pageSize: 100 })]); tree.value = Array.isArray(treeData) ? treeData : [treeData]; staff.value = staffData.records } catch (error) { ElMessage.error(error instanceof Error ? error.message : '组织数据加载失败') } finally { loading.value = false } })
function nodeLabel(data: OrganizationNode) { return String(data.label ?? data.name ?? data.companyName ?? data.departmentName ?? '组织节点') }
</script>

<template>
  <div v-loading="loading" class="page"><div class="page-header"><div><h1 class="page-title">组织架构</h1><p class="page-description">查看公司、部门与人员关系</p></div></div><div class="organization-grid"><section class="surface tree-panel"><h2>组织树</h2><el-tree :data="tree" node-key="id" default-expand-all :props="{ children: 'children' }"><template #default="{ data }"><span>{{ nodeLabel(data) }}</span></template></el-tree></section><section class="surface"><div class="toolbar"><h2>员工目录</h2><el-input v-model="keyword" clearable placeholder="搜索姓名或部门" style="width: 240px" /></div><el-table :data="staff.filter((item) => !keyword || item.name.includes(keyword) || item.department?.includes(keyword))" style="margin-top: 14px"><el-table-column prop="staffNumber" label="工号" width="170" /><el-table-column prop="name" label="姓名" width="100" /><el-table-column prop="company" label="公司" min-width="140" /><el-table-column prop="department" label="部门" min-width="120" /><el-table-column prop="position" label="职位" min-width="140" /><el-table-column prop="phone" label="手机号" width="140" /></el-table></section></div></div>
</template>

<style scoped>.organization-grid { display: grid; grid-template-columns: 300px minmax(0,1fr); gap: 16px; }.surface h2 { margin: 0; font-size: 17px; }.tree-panel { min-height: 560px; }.tree-panel :deep(.el-tree) { margin-top: 18px; background: transparent; }.tree-panel :deep(.el-tree-node__content) { height: 36px; border-radius: 7px; }</style>
