import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
  server: {
    port: 3005,
    // host: true - 允许局域网设备访问运营台
    host: true,
    proxy: {
      '/adminApi': {
        target: 'http://localhost:38100',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/adminApi/, '/live/admin')
      }
    }
  }
})
