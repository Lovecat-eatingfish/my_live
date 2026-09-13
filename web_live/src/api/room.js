import request from './request'

// 直播间列表（后端LivingRoomReqVO为查询参数绑定，须走query）
export const listRoom = (params) => request.post('/living/list', null, { params })

// 开播
// 开播（roomName/covertImg 为主播自定义直播间名称与封面 URL，可不传走默认）
export const startLiving = (type, roomName, covertImg, payType, ticketPrice) => request.post('/living/startingLiving', null, { params: { type, roomName, covertImg, payType, ticketPrice } })

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

// ==================== 连麦（5572 信令） ====================

// 主播邀请观众连麦
export const inviteLinkMic = (roomId, guestUserId) =>
  request.post('/living/linkMic/invite', null, { params: { roomId, guestUserId } })

// 观众接受连麦
export const acceptLinkMic = (linkMicId) => request.post('/living/linkMic/accept', null, { params: { linkMicId } })

// 挂断连麦
export const hangUpLinkMic = (roomId) => request.post('/living/linkMic/hangUp', null, { params: { roomId } })

// 付费直播间门票（金币直扣，幂等）
export const buyTicket = (roomId) => request.post('/living/ticket/buy', null, { params: { roomId } })
