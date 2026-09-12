import { defineStore } from 'pinia'
import { ref } from 'vue'
import { initPage, logout } from '@/api/user'
import { getBalance } from '@/api/bank'

// 后端网关优先从 qytk cookie 取token，浏览器与后端不同域时cookie写不进去，前端自行补一份
function saveTokenCookie(t) {
  document.cookie = `qytk=${t}; path=/; max-age=${30 * 24 * 3600}`
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('qiyu_token') || '')
  const userInfo = ref({ userId: null, nickName: '', avatar: '', loginStatus: false, showStartLivingBtn: false })
  const balance = ref(0)

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

  // 拉取金币余额（送礼/抢红包/充值后调用，保持顶栏数字最新）
  async function refreshBalance() {
    if (!token.value) return
    try {
      const vo = await getBalance()
      balance.value = Number(vo.data) || 0
    } catch {
      // 余额拉取失败不打断业务
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
    balance.value = 0
    userInfo.value = { userId: null, nickName: '', avatar: '', loginStatus: false, showStartLivingBtn: false }
  }

  return { token, userInfo, balance, fetchUserInfo, refreshBalance, setToken, logoutUser }
})
