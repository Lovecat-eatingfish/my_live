<template>
  <div class="login-page">
    <div class="login-box">
      <h1>旗鱼直播</h1>
      <div class="form-item">
        <input v-model="phone" placeholder="请输入手机号" maxlength="11" />
      </div>
      <div class="form-item" v-if="step === 2">
        <input v-model="code" placeholder="请输入验证码" maxlength="6" />
      </div>
      <el-button type="primary" :loading="loading" class="btn" @click="handleSendCode" v-if="step === 1">
        发送验证码
      </el-button>
      <el-button type="primary" :loading="loading" class="btn" @click="handleLogin" v-else>
        登录
      </el-button>
      <div class="tips" v-if="step === 2">验证码已发送，请注意查收</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { sendLoginCode, login } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const phone = ref('')
const code = ref('')
const step = ref(1)
const loading = ref(false)

async function handleSendCode() {
  if (!phone.value || phone.value.length !== 11) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  loading.value = true
  try {
    await sendLoginCode(phone.value)
    step.value = 2
  } finally {
    loading.value = false
  }
}

async function handleLogin() {
  if (!code.value || code.value.length !== 6) {
    ElMessage.warning('请输入6位验证码')
    return
  }
  loading.value = true
  try {
    const vo = await login(phone.value, code.value)
    userStore.setToken(vo.data.token)
    userStore.userInfo = { ...vo.data, loginStatus: true }
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--sq-blue) 0%, #764ba2 100%);
}
.login-box {
  background: rgba(255,255,255,0.1);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  padding: 40px;
  width: 360px;
  text-align: center;
}
.login-box h1 { color: #fff; margin-bottom: 30px; font-size: 28px; }
.form-item { margin-bottom: 16px; }
.form-item input {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: 8px;
  background: rgba(255,255,255,0.15);
  color: #fff;
  font-size: 16px;
  outline: none;
  transition: border-color 0.3s;
}
.form-item input:focus { border-color: #fff; }
.form-item input::placeholder { color: rgba(255,255,255,0.6); }
.btn { width: 100%; height: 46px; font-size: 16px; margin-top: 8px; }
.tips { color: rgba(255,255,255,0.8); font-size: 13px; margin-top: 12px; }
</style>
