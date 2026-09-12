import request from './request'

/**
 * 上传图片（直播间封面等）
 * @param {File} file 图片文件
 * @returns {Promise<{code,data:string}>} data 为 MinIO 公开访问 URL
 */
export const uploadImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/resource/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000
  })
}
