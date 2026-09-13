import request from './request'

// 主播收礼榜 period: day / week
export const anchorGiftRank = (period = 'day') => request.post('/rank/anchorGift', null, { params: { period } })

// 单房间本场贡献榜 top10
export const roomGiftRank = (roomId) => request.post('/rank/roomGift', null, { params: { roomId } })

// 人气榜 top10
export const heatRank = () => request.post('/rank/heat')
