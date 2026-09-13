<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo">◈ 旗鱼运营台</div>
      <nav class="nav">
        <router-link v-for="m in menus" :key="m.path" :to="m.path"
          :class="['nav-item', { active: $route.path === m.path }]">
          <span class="nav-icon">{{ m.icon }}</span>{{ m.label }}
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <el-button text size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
const menus = [
  { path: '/dash', label: '运营仪表盘', icon: '▩' },
  { path: '/recon', label: '对账中心', icon: '▦' },
  { path: '/audit', label: '视频审核', icon: '✓' },
  { path: '/snapshot', label: '直播巡查', icon: '◎' },
  { path: '/risk', label: '敏感词管理', icon: '⚑' },
  { path: '/tag', label: '标签管理', icon: '◈' },
  { path: '/video', label: '内容管理', icon: '▶' },
  { path: '/user', label: '用户管理', icon: '☰' },
  { path: '/living', label: '直播管理', icon: '◉' },
]

function handleLogout() {
  localStorage.removeItem('admin_token')
  router.push('/login')
}
</script>

<style scoped>
.layout { display: flex; min-height: 100vh; }
.sidebar {
  width: 210px; flex-shrink: 0;
  background: var(--surface); border-right: 1px solid var(--line);
  display: flex; flex-direction: column;
  position: sticky; top: 0; height: 100vh;
}
.logo { padding: 20px 18px; font-weight: 700; font-size: 15px; color: var(--brand); border-bottom: 1px solid var(--line); }
.nav { flex: 1; padding: 10px; display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 8px;
  color: var(--ink-2); font-size: 14px;
  transition: all 0.15s;
}
.nav-item:hover { background: var(--brand-weak); color: var(--brand); }
.nav-item.active { background: var(--brand); color: #fff; }
.nav-icon { width: 18px; text-align: center; }
.sidebar-footer { padding: 14px; border-top: 1px solid var(--line); }
.main { flex: 1; padding: 24px 28px; min-width: 0; }
</style>
