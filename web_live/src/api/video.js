import request from './request'
import { ElMessage } from 'element-plus'

// 大文件 multipart 上传统一走 fetch（axios+FormData 在部分环境下请求完成后 promise 不决）
async function uploadFormData(url, formData) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'token': localStorage.getItem('qiyu_token') || '' },
    body: formData
  })
  const vo = await res.json()
  if (vo.code !== 200) {
    ElMessage.error(vo.msg || '上传失败')
    throw vo
  }
  return vo
}

// 上传视频文件（MP4/WebM/MOV），返回播放 URL
export const uploadVideo = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return uploadFormData('/api/video/uploadVideo', formData)
}

// 发布视频（videoUrl/coverUrl 为已上传地址）
export const publishVideo = (data) => request.post('/video/publish', data)

// 视频流列表（tagId=0 全部）
export const listVideos = (tagId = 0, page = 1, pageSize = 20) =>
  request.post('/video/list', null, { params: { tagId, page, pageSize } })

// 视频详情（播放量+1）
export const videoDetail = (id) => request.post('/video/detail', null, { params: { id } })

// 点赞 / 取消点赞
export const likeVideo = (id, isLike) => request.post('/video/like', null, { params: { id, isLike } })

// 收藏 / 取消收藏
export const favoriteVideo = (id, isFavorite) => request.post('/video/favorite', null, { params: { id, isFavorite } })

// 分享计数
export const shareVideo = (id) => request.post('/video/share', null, { params: { id } })

// 标签列表
export const listVideoTags = () => request.post('/video/tags')

// 评论列表
export const listComments = (id, page = 1, pageSize = 20) =>
  request.post('/video/comment/list', null, { params: { id, page, pageSize } })

// 发表评论
export const addComment = (id, content) => request.post('/video/comment/add', null, { params: { id, content } })

// 删除本人评论
export const deleteComment = (commentId) => request.post('/video/comment/delete', null, { params: { commentId } })
