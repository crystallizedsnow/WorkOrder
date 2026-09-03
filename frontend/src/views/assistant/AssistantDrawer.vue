<script setup lang="ts">
import AssistantChat from './AssistantChat.vue'
defineProps<{ modelValue: boolean }>(); defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>
<template><el-drawer :model-value="modelValue" title="AI 助手" size="520px" :with-header="false" @update:model-value="$emit('update:modelValue', $event)"><AssistantChat /></el-drawer></template>
<style scoped>:deep(.el-drawer__body) { padding:0; }</style>
