import { defineStore } from 'pinia'
import { ref } from 'vue'
import { initPage, logout } from '@/api/user'

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
  }

  async function logoutUser() {
    await logout()
    token.value = ''
    userInfo.value = { userId: null, nickName: '', avatar: '', loginStatus: false, showStartLivingBtn: false }
  }

  return { token, userInfo, fetchUserInfo, setToken, logoutUser }
})
