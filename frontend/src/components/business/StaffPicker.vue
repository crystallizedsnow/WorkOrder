<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { organizationApi } from '@/api/modules/organization'
import type { Staff } from '@/api/types'

defineProps<{ modelValue?: number; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined]; select: [staff: Staff] }>()
const staff = ref<Staff[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try { staff.value = await organizationApi.allStaff() } finally { loading.value = false }
})
</script>

<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder ?? '请选择员工'"
    filterable clearable :loading="loading" style="width: 100%"
    @update:model-value="(value: number | undefined) => emit('update:modelValue', value)"
    @change="(id: number) => { const item = staff.find((s) => s.id === id); if (item) emit('select', item) }"
  >
    <el-option v-for="item in staff" :key="item.id" :label="`${item.name} · ${item.department}`" :value="item.id" />
  </el-select>
</template>
