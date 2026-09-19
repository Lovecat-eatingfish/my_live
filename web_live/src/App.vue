<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 全局初始化：任意页面直达（刷新/分享链接）时顶栏登录态和余额保持正确
onMounted(() => {
  if (userStore.token) {
    userStore.fetchUserInfo()
    userStore.refreshBalance()
  }
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; background: #0a0a0a; color: #fff; }
a { text-decoration: none; color: inherit; }
</style>
