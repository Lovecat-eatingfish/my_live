import request from './request'

// 直播间列表（后端LivingRoomReqVO为查询参数绑定，须走query）
export const listRoom = (params) => request.post('/living/list', null, { params })

// 开播
// 开播（roomName/covertImg 为主播自定义直播间名称与封面 URL，可不传走默认）
export const startLiving = (type, roomName, covertImg) => request.post('/living/startingLiving', null, { params: { type, roomName, covertImg } })

// 关播
export const closeLiving = (roomId) => request.post('/living/closeLiving', null, { params: { roomId } })

// 在线观众数
export const onlineCount = (roomId) => request.post('/living/onlineCount', null, { params: { roomId } })

// 我进行中的直播间（主播刷新浏览器后恢复用）
export const myLivingRoom = () => request.post('/living/myLivingRoom')

// 获取IM配置
export const getImConfig = () => request.post('/im/getImConfig')

// 主播配置
export const anchorConfig = (roomId) => request.post('/living/anchorConfig', null, { params: { roomId } })
