import request from './request'
import { ElMessage } from 'element-plus'

// 大文件 multipart 上传统一走 XHR：
// 1. axios+FormData 在部分环境下请求完成后 promise 不决（见历史问题）
// 2. fetch 的 res.json() 依赖响应体流读取，被浏览器扩展（如视频下载类）劫持响应流时会永远挂起，
//    表现为"请求 200 已到但前端一直转圈"；XHR 的 load 事件在传输结束时必触发，不依赖 body 流
// 同时支持进度回调与中止（xhrRef 存放当前请求，供取消上传用）
export function uploadFormData(url, formData, { onProgress, xhrRef } = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    if (xhrRef) xhrRef.value = xhr
    xhr.open('POST', url)
    xhr.setRequestHeader('token', localStorage.getItem('qiyu_token') || '')
    // 15 分钟超时兜底：正常链路几秒~几分钟，超时说明链路挂了，不能让用户干等
    xhr.timeout = 15 * 60 * 1000
    if (onProgress) {
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100))
      }
    }
    xhr.onload = () => {
      let vo
      try {
        vo = JSON.parse(xhr.responseText)
      } catch {
        reject({ msg: `响应异常(${xhr.status})` })
        return
      }
      if (xhr.status === 200 && vo.code === 200) {
        resolve(vo)
      } else {
        ElMessage.error(vo.msg || `上传失败(${xhr.status})`)
        reject(vo)
      }
    }
    xhr.onerror = () => reject({ msg: '网络异常，上传失败' })
    xhr.ontimeout = () => {
      ElMessage.error('上传超时，请检查网络或换个小文件试试')
      reject({ msg: '上传超时' })
    }
    xhr.onabort = () => reject({ msg: '已取消上传', aborted: true })
    xhr.send(formData)
  })
}

// 上传视频文件（MP4/WebM/MOV），返回播放 URL
export const uploadVideo = (file, opts = {}) => {
  const formData = new FormData()
  formData.append('file', file)
  return uploadFormData('/api/video/uploadVideo', formData, opts)
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

// 记录观看历史（播放≥3秒时上报）
export const recordHistory = (id) => request.post('/video/history', null, { params: { id } })

// 我的观看历史 / 我发布的 / 我收藏的 / 我点赞的
export const myHistory = (page = 1, pageSize = 20) => request.post('/video/my/history', null, { params: { page, pageSize } })
export const myList = (page = 1, pageSize = 20) => request.post('/video/my/list', null, { params: { page, pageSize } })
export const myFavorites = (page = 1, pageSize = 20) => request.post('/video/my/favorites', null, { params: { page, pageSize } })
export const myLikes = (page = 1, pageSize = 20) => request.post('/video/my/likes', null, { params: { page, pageSize } })
