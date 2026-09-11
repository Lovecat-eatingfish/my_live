import request from './request'

// 直播间列表
export const listRoom = (params) => request.post('/living/list', params)

// 开播
export const startLiving = (type) => request.post('/living/startingLiving', null, { params: { type } })

// 关播
export const closeLiving = (roomId) => request.post('/living/closeLiving', null, { params: { roomId } })

// 获取IM配置
export const getImConfig = () => request.post('/im/getImConfig')

// 主播配置
export const anchorConfig = (roomId) => request.post('/living/anchorConfig', null, { params: { roomId } })
