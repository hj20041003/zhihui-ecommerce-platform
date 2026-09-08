import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发服务器 5173，/api 代理到网关 19080（同源代理，无 CORS 问题）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:19080',
        changeOrigin: true,
      },
    },
  },
})
