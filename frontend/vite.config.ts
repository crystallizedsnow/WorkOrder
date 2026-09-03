import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          charts: ['echarts'],
          elementPlus: ['element-plus', '@element-plus/icons-vue'],
          markdown: ['markdown-it', 'dompurify'],
          vue: ['vue', 'vue-router', 'pinia'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/backend': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/backend/, ''),
      },
      '/api/assistant': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/assistant/, ''),
      },
    },
  },
})
