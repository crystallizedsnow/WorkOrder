<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
const html = computed(() => DOMPurify.sanitize(markdown.render(props.content)))
</script>

<template><div class="markdown" v-html="html" /></template>

<style scoped>
.markdown { line-height: 1.7; overflow-wrap: anywhere; }.markdown :deep(p) { margin: 0 0 10px; }.markdown :deep(p:last-child) { margin-bottom: 0; }.markdown :deep(pre) { padding: 12px; overflow: auto; color: #edf1ff; background: #172033; border-radius: 8px; }.markdown :deep(code) { font-family: "SFMono-Regular", Consolas, monospace; }.markdown :deep(:not(pre) > code) { padding: 2px 5px; color: #344fb4; background: #eef2ff; border-radius: 4px; }.markdown :deep(a) { color: var(--wo-primary); text-decoration: underline; }
</style>
