<template>
  <div class="home-page">
    <!-- 顶部导航 -->
    <header class="nav-bar">
      <div class="nav-left">
        <span class="logo">🎬 旗鱼直播</span>
      </div>
      <div class="nav-right">
        <template v-if="userStore.userInfo.loginStatus">
          <img :src="userStore.userInfo.avatar || defaultAvatar" class="avatar" />
          <span class="nickname">{{ userStore.userInfo.nickName }}</span>
          <el-button size="small" @click="$router.push('/wallet')">钱包</el-button>
          <el-button size="small" type="danger" @click="handleLogout">退出</el-button>
        </template>
        <el-button size="small" type="primary" v-else @click="$router.push('/login')">登录</el-button>
      </div>
    </header>

    <!-- 类型筛选 -->
    <div class="filter-bar">
      <span
        v-for="t in roomTypes"
        :key="t.value"
        :class="['type-tag', { active: currentType === t.value }]"
        @click="currentType = t.value"
      >{{ t.label }}</span>
    </div>

    <!-- 直播间列表 -->
    <div class="room-grid">
      <div v-for="room in rooms" :key="room.id" class="room-card" @click="$router.push(`/room/${room.id}`)">
        <div class="cover-wrap">
          <img :src="room.covertImg || defaultCover" class="cover" />
          <span class="watch-num">👁 {{ room.watchNum }}</span>
          <span class="good-num">👍 {{ room.goodNum }}</span>
        </div>
        <div class="room-info">
          <div class="room-name">{{ room.roomName }}</div>
          <div class="anchor-info">主播ID: {{ room.anchorId }}</div>
        </div>
      </div>
      <div v-if="rooms.length === 0" class="empty">暂无直播间</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listRoom } from '@/api/room'

const router = useRouter()
const userStore = useUserStore()
const rooms = ref([])
const currentType = ref(1)
const roomTypes = [
  { label: '推荐', value: 1 },
  { label: '游戏', value: 2 },
  { label: '娱乐', value: 3 },
  { label: '赛事', value: 4 },
]

const defaultAvatar = 'https://via.placeholder.com/40/667eea/fff?text=U'
const defaultCover = 'https://via.placeholder.com/320x180/1a1a2e/667eea?text=Live'

async function fetchRooms() {
  const vo = await listRoom({ type: currentType.value, page: 1, pageSize: 20 })
  rooms.value = vo.data?.list || []
}

// 类型切换时自动请求
watch(currentType, () => fetchRooms())

async function handleLogout() {
  await userStore.logoutUser()
  router.push('/')
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  await fetchRooms()
})
</script>

<style scoped>
.home-page { min-height: 100vh; background: #0a0a0a; }
.nav-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 32px; background: #161625; border-bottom: 1px solid #222;
}
.logo { font-size: 20px; font-weight: bold; color: #667eea; }
.nav-right { display: flex; align-items: center; gap: 12px; }
.avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
.nickname { color: #ddd; font-size: 14px; }
.filter-bar { display: flex; gap: 16px; padding: 20px 32px; }
.type-tag {
  padding: 6px 18px; border-radius: 20px; cursor: pointer; font-size: 14px;
  color: #888; background: #1e1e2e; transition: all 0.2s;
}
.type-tag.active { background: #667eea; color: #fff; }
.room-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px; padding: 0 32px 40px;
}
.room-card { background: #161625; border-radius: 12px; overflow: hidden; cursor: pointer; transition: transform 0.2s; }
.room-card:hover { transform: translateY(-4px); }
.cover-wrap { position: relative; }
.cover { width: 100%; height: 140px; object-fit: cover; display: block; }
.watch-num, .good-num { position: absolute; bottom: 6px; font-size: 12px; color: #fff; background: rgba(0,0,0,0.5); padding: 2px 6px; border-radius: 4px; }
.watch-num { right: 6px; }
.good-num { left: 6px; }
.room-info { padding: 12px; }
.room-name { font-size: 14px; color: #ddd; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.anchor-info { font-size: 12px; color: #666; }
.empty { grid-column: 1/-1; text-align: center; color: #444; padding: 60px 0; }
</style>
