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

// 响应拦截器：统一错误处理（config.silent 为 true 时不弹全局错误提示，由调用方自行消化）
client.interceptors.response.use(
  (res) => {
    const vo = res.data
    if (vo.code && vo.code !== 200) {
      if (!res.config.silent) ElMessage.error(vo.msg || '请求失败')
      return Promise.reject(vo)
    }
    return vo
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      // 登录失效：清除本地token并跳转登录页
      localStorage.removeItem('qiyu_token')
      document.cookie = 'qytk=; path=/; max-age=0'
      const { code, msg } = err.response.data || {}
      ElMessage.error(msg || '登录已失效，请重新登录')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
      return Promise.reject({ code, msg })
    }
    if (!err.config?.silent) ElMessage.error(err.message || '网络异常')
    return Promise.reject(err)
  }
)

export default client
