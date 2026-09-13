<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="brand-mark">◈</span>
        <div>
          <div class="brand-name">旗鱼运营台</div>
          <div class="brand-sub">内部管理系统 · 请使用运营账号登录</div>
        </div>
      </div>
      <el-input v-model="username" placeholder="账号" size="large" @keyup.enter="handleLogin" />
      <el-input v-model="password" type="password" placeholder="密码" size="large" show-password @keyup.enter="handleLogin" />
      <el-button type="primary" size="large" :loading="loading" @click="handleLogin">登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post } from '@/api'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)

async function handleLogin() {
  if (!username.value || !password.value) {
    ElMessage.warning('输入账号和密码')
    return
  }
  loading.value = true
  try {
    const vo = await post('/auth/login', { username: username.value, password: password.value })
    localStorage.setItem('admin_token', vo.data.token)
    router.push('/recon')
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  background:
    linear-gradient(160deg, rgba(15, 107, 92, 0.06), transparent 40%),
    var(--bg);
}
.login-card {
  width: 380px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 36px 32px 32px;
  display: flex; flex-direction: column; gap: 14px;
  box-shadow: 0 10px 40px rgba(26, 29, 31, 0.06);
}
.login-brand { display: flex; gap: 14px; align-items: center; margin-bottom: 10px; }
.brand-mark {
  width: 44px; height: 44px; border-radius: 10px;
  background: var(--brand); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px;
}
.brand-name { font-size: 18px; font-weight: 700; letter-spacing: 0.02em; }
.brand-sub { font-size: 12px; color: var(--ink-2); margin-top: 3px; }
</style>
