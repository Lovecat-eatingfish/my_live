import axios from 'axios'
import { ElMessage } from 'element-plus'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：注入登录token
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('qiyu_token')
  if (token) {
    config.headers['token'] = token
  }
  return config
})

// 响应拦截器：统一错误处理
client.interceptors.response.use(
  (res) => {
    const vo = res.data
    if (vo.code && vo.code !== 200) {
      ElMessage.error(vo.msg || '请求失败')
      return Promise.reject(vo)
    }
    return vo
  },
  (err) => {
    ElMessage.error(err.message || '网络异常')
    return Promise.reject(err)
  }
)

export default client
