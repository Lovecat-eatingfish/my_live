import axios from 'axios'
import { ElMessage } from 'element-plus'

const client = axios.create({ baseURL: '/adminApi', timeout: 15000 })

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) config.headers['adminToken'] = token
  return config
})

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
    if (err.response?.status === 401) {
      localStorage.removeItem('admin_token')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
      return Promise.reject(err)
    }
    ElMessage.error(err.message || '网络异常')
    return Promise.reject(err)
  }
)

// 后台接口全部为查询参数/表单绑定，统一走 query
export const post = (url, params = {}) => client.post(url, null, { params })
export default client
