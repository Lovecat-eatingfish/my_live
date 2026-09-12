import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:38080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/live/api')
      },
      // WebRTC 推流信令：转发到 SRS HTTP API（宿主机 31985 映射容器 1985）
      '/rtc': {
        target: 'http://127.0.0.1:1985',
        changeOrigin: true
      }
    }
  }
})
