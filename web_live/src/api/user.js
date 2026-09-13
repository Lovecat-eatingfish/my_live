import request from './request'

// 发送验证码（后端为表单/查询参数绑定，须走query）
export const sendLoginCode = (phone) => request.post('/userLogin/sendLoginCode', null, { params: { phone } })

// 登录
export const login = (phone, code) => request.post('/userLogin/login', null, { params: { phone, code } })

// 首页初始化
export const initPage = () => request.post('/home/initPage')

// 修改昵称/头像（不传的字段保持不变）
export const updateProfile = (data) => request.post('/user/updateProfile', null, { params: data })

// 退出登录
export const logout = () => Promise.resolve(localStorage.removeItem('qiyu_token'))

// ==================== 关系链（关注/粉丝）与个人主页 ====================

// 关注
export const followUser = (followUserId) => request.post('/user/follow', null, { params: { followUserId } })

// 取关
export const unfollowUser = (followUserId) => request.post('/user/unfollow', null, { params: { followUserId } })

// 是否已关注
export const isFollowUser = (targetUserId) => request.post('/user/isFollow', null, { params: { targetUserId } })

// 我关注的人
export const followList = (page = 1, pageSize = 20) => request.post('/user/followList', null, { params: { page, pageSize } })

// 我的粉丝
export const fansList = (page = 1, pageSize = 20) => request.post('/user/fansList', null, { params: { page, pageSize } })

// 个人主页聚合（targetUserId 不传=自己）
export const getUserProfile = (targetUserId) =>
  request.post('/user/profile', null, { params: targetUserId ? { targetUserId } : {} })

// ==================== 通知中心 ====================

// 通知分页
export const notifyList = (page = 1, pageSize = 20) => request.post('/user/notify/list', null, { params: { page, pageSize } })

// 标记已读（notifyId 不传=全部已读）
export const notifyRead = (notifyId) => request.post('/user/notify/read', null, { params: notifyId ? { notifyId } : {} })

// 未读数
export const notifyUnreadCount = () => request.post('/user/notify/unreadCount')
