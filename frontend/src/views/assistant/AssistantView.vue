<script setup lang="ts">import AssistantChat from './AssistantChat.vue'</script>
<template><div class="page assistant-page"><div class="page-header"><div><h1 class="page-title">AI 助手</h1><p class="page-description">使用自然语言查询和操作工单，写操作会先预演再确认</p></div></div><section class="assistant-surface"><AssistantChat /></section></div></template>
<style scoped>.assistant-page { height:calc(100vh - 134px); }.assistant-surface { flex:1; min-height:0; overflow:hidden; background:#fff; border:1px solid var(--wo-border); border-radius:var(--wo-radius); }</style>
