import request from './request'

// 主播获取推流地址（OBS 推流用；推流服务未部署时静默失败，面板上已有兜底提示）
export const createPushUrl = (roomId) => request.post('/stream/createPushUrl', null, { params: { roomId }, silent: true })

// 查询房间流状态: status 0=未开播 1=推流中 2=异常（轮询调用，推流服务未部署时静默失败，不弹错误提示）
export const getStreamStatus = (roomId) => request.post('/stream/status', null, { params: { roomId }, silent: true })

// 观众获取 HLS 播放地址
export const getPlayUrl = (roomId) => request.post('/stream/playUrl', null, { params: { roomId } })

// 获取录制回放列表
export const getRecordList = (roomId) => request.post('/stream/records', null, { params: { roomId } })
