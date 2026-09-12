import { ElMessage } from 'element-plus'

/**
 * 上传图片（直播间封面/视频封面）
 * @param {File} file 图片文件
 * @returns {Promise<{code,data:string}>} data 为 MinIO 公开访问 URL
 * 大文件 multipart 上传统一走 fetch（axios+FormData 在部分环境下请求完成后 promise 不决）
 */
export const uploadImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return fetch('/api/resource/upload', {
    method: 'POST',
    headers: { 'token': localStorage.getItem('qiyu_token') || '' },
    body: formData
  }).then(async (res) => {
    const vo = await res.json()
    if (vo.code !== 200) {
      ElMessage.error(vo.msg || '上传失败')
      return Promise.reject(vo)
    }
    return vo
  })
}
