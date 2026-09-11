import request from './request'

// 礼物列表
export const listGift = () => request.post('/gift/listGift')

// 发送礼物
export const sendGift = (data) => request.post('/gift/send', data)
