<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: number; label?: string }>()
const type = computed(() => {
  if ([600].includes(props.status)) return 'success'
  if ([270, 410, 670].includes(props.status)) return 'danger'
  if ([500].includes(props.status)) return 'warning'
  if ([700].includes(props.status)) return 'info'
  return 'primary'
})
</script>

<template><el-tag :type="type" effect="light" round>{{ label ?? status }}</el-tag></template>
