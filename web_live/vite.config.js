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
    // 监听所有网卡：后端拼的 MinIO 公网地址(public-endpoint)指向局域网 IP，
    // 只绑回环会导致视频/截帧/审核封面图全部 404
    host: true,
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
      },
      // MinIO 对象存储（封面/回放）：Windows 防火墙拦 9000 直连，统一走 vite 代理
      // todo： 这个后端地址 需要和 前端协商一下 不然很容易出现 访问不到的问题
      '/minio': {
        target: 'http://127.0.0.1:9000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/minio/, '')
      }
    }
  }
})
