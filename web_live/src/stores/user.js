import { defineStore } from 'pinia'
import { ref } from 'vue'
import { initPage, logout } from '@/api/user'

// 后端网关优先从 qytk cookie 取token，浏览器与后端不同域时cookie写不进去，前端自行补一份
function saveTokenCookie(t) {
  document.cookie = `qytk=${t}; path=/; max-age=${30 * 24 * 3600}`
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('qiyu_token') || '')
  const userInfo = ref({ userId: null, nickName: '', avatar: '', loginStatus: false, showStartLivingBtn: false })

  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const vo = await initPage()
      userInfo.value = vo.data
    } catch {
      token.value = ''
      localStorage.removeItem('qiyu_token')
    }
  }

  function setToken(t) {
    token.value = t
    localStorage.setItem('qiyu_token', t)
    saveTokenCookie(t)
  }

  async function logoutUser() {
    await logout()
    token.value = ''
    localStorage.removeItem('qiyu_token')
    saveTokenCookie('')
    userInfo.value = { userId: null, nickName: '', avatar: '', loginStatus: false, showStartLivingBtn: false }
  }

  return { token, userInfo, fetchUserInfo, setToken, logoutUser }
})
