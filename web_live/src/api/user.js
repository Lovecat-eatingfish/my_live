import request from './request'

// 发送验证码
export const sendLoginCode = (phone) => request.post('/userLogin/sendLoginCode', { phone })

// 登录
export const login = (phone, code) => request.post('/userLogin/login', null, { params: { phone, code } })

// 首页初始化
export const initPage = () => request.post('/home/initPage')

// 退出登录
export const logout = () => Promise.resolve(localStorage.removeItem('qiyu_token'))
