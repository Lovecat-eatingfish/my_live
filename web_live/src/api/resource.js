import { ElMessage } from 'element-plus'

/**
 * 上传图片（直播间封面/视频封面）
 * @param {File} file 图片文件
 * @returns {Promise<{code,data:string}>} data 为 MinIO 公开访问 URL
 * 用 XHR 而非 fetch：fetch 的 res.json() 依赖响应体流读取，被浏览器扩展劫持响应流时会永远挂起；
 * XHR 的 load 事件在传输结束时必触发（同 api/video.js 的 uploadFormData）
 */
export const uploadImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/resource/upload')
    xhr.setRequestHeader('token', localStorage.getItem('qiyu_token') || '')
    xhr.timeout = 60 * 1000
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
      ElMessage.error('上传超时')
      reject({ msg: '上传超时' })
    }
    xhr.send(formData)
  })
}
