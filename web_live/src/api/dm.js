import request from './request'

/** 会话列表（含对方昵称/头像/未读数） */
export const dmConversations = () => request.get('/dm/conversations')

/** 与某人的历史消息（最新 size 条，正序） */
export const dmHistory = (peerUid, size = 50) => request.get('/dm/history', { params: { peerUid, size } })

/** 打开会话，清零未读 */
export const dmMarkRead = (peerUid) => request.post('/dm/markRead', null, { params: { peerUid } })

/** 全部会话未读总数 */
export const dmUnreadTotal = () => request.get('/dm/unreadTotal')
