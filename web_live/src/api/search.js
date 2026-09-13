import request from './request'

// 全局搜索：三分栏返回 { rooms, videos, users }
export const searchAll = (keyword) => request.post('/search/all', null, { params: { keyword } })
