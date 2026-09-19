import request from './request'

// 直播间列表（后端LivingRoomReqVO为查询参数绑定，须走query）
export const listRoom = (params) => request.post('/living/list', null, { params })

// 开播
// 开播（roomName/covertImg 为主播自定义直播间名称与封面 URL，可不传走默认）
export const startLiving = (type, roomName, covertImg, payType, ticketPrice, recordEnabled) => request.post('/living/startingLiving', null, { params: { type, roomName, covertImg, payType, ticketPrice, recordEnabled } })

// 关播
export const closeLiving = (roomId) => request.post('/living/closeLiving', null, { params: { roomId } })

// 在线观众数
export const onlineCount = (roomId) => request.post('/living/onlineCount', null, { params: { roomId } })

// 我进行中的直播间（主播刷新浏览器后恢复用）
export const myLivingRoom = () => request.post('/living/myLivingRoom')

// 获取IM配置
export const getImConfig = () => request.post('/im/getImConfig')

/** 主播发起投票（options 为选项数组） */
export const createVote = (roomId, title, options, durationSec) =>
  request.post('/living/vote/create', null, { params: { roomId, title, options: JSON.stringify(options), durationSec } })

/** 观众投票（一人一票） */
export const castVote = (roomId, optionIndex) => request.post('/living/vote/cast', null, { params: { roomId, optionIndex } })

/** 当前进行中的投票 */
export const currentVote = (roomId) => request.post('/living/vote/current', null, { params: { roomId } })

/** 主播设置直播间公告 */
export const setAnnouncement = (roomId, announcement) => request.post('/living/setAnnouncement', null, { params: { roomId, announcement } })

/** 任命/移除房间管理员 */
export const appointRoomAdmin = (roomId, adminUserId) => request.post('/living/roomAdmin/appoint', null, { params: { roomId, adminUserId } })
export const removeRoomAdmin = (roomId, adminUserId) => request.post('/living/roomAdmin/remove', null, { params: { roomId, adminUserId } })

/** 禁言/解禁（主播或管理员） */
export const muteRoomUser = (roomId, muteUserId, minutes = 30) => request.post('/living/roomAdmin/mute', null, { params: { roomId, muteUserId, minutes } })
export const unmuteRoomUser = (roomId, muteUserId) => request.post('/living/roomAdmin/unmute', null, { params: { roomId, muteUserId } })

/** 口令抽奖：主播发起（keyword/durationSec/winnerCount/rewardCoins） */
export const createLottery = (roomId, keyword, durationSec, winnerCount, rewardCoins) =>
  request.post('/living/lottery/create', null, { params: { roomId, keyword, durationSec, winnerCount, rewardCoins } })

/** 直播分区列表（首页动态 tab） */
export const listCategories = () => request.post('/living/categories')

/** 关注 tab：关注的主播中正在开播的房间 */
export const followRooms = () => request.post('/living/followRooms')

// 主播配置
// silent: true —— 付费直播间未购票时返回 10114，由 RoomPage 弹购票窗消化，
// 不能让全局拦截器直接 reject（否则购票弹窗永远走不到）
export const anchorConfig = (roomId) => request.post('/living/anchorConfig', null, { params: { roomId }, silent: true })

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
