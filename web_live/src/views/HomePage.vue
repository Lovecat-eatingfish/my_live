<template>
  <div class="home-page">
    <!-- 顶部导航 -->
    <header class="nav-bar">
      <div class="nav-left">
        <span class="logo">🎬 旗鱼直播</span>
        <span :class="['nav-tab', { active: $route.path === '/' }]" @click="$router.push('/')">直播</span>
        <span :class="['nav-tab', { active: $route.path.startsWith('/video') }]" @click="$router.push('/video')">视频</span>
      </div>
      <div class="nav-right">
        <template v-if="userStore.userInfo.loginStatus">
          <img :src="userStore.userInfo.avatar || defaultAvatar" class="avatar" title="个人设置" @click="profileVisible = true" />
          <span class="nickname">{{ userStore.userInfo.nickName }}</span>
          <span class="balance-chip" @click="$router.push('/wallet')" title="去充值">
            <span class="coin-icon">🪙</span>{{ formatBalance }}
          </span>
          <el-button v-if="userStore.userInfo.showStartLivingBtn" size="small" type="success" @click="startVisible = true">开播</el-button>
          <el-button v-if="livingRoomId" size="small" type="warning" @click="$router.push(`/room/${livingRoomId}`)">回到我的直播间</el-button>
          <el-button size="small" @click="$router.push('/wallet')">钱包</el-button>
          <el-button size="small" @click="$router.push('/recon')">对账</el-button>
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

    <!-- 开播设置弹窗：起名 + 上传封面 -->
    <StartLivingDialog ref="startDialogRef" v-model="startVisible" @confirm="handleStartLiving" />
    <UserProfileDialog v-model="profileVisible" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listRoom, startLiving, myLivingRoom } from '@/api/room'
import StartLivingDialog from '@/components/StartLivingDialog.vue'
import UserProfileDialog from '@/components/UserProfileDialog.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const rooms = ref([])
const currentType = ref(1)
const roomTypes = [
  { label: '娱乐', value: 1 },
  { label: '游戏', value: 2 },
  { label: '赛事', value: 3 },
  { label: '带货', value: 4 },
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

const formatBalance = computed(() => {
  const n = Number(userStore.balance) || 0
  return n >= 10000 ? (n / 10000).toFixed(1) + 'w' : String(n)
})

// 开播：由 StartLivingDialog 收集名称与封面后回调，创建直播间并跳转主播端
const startVisible = ref(false)
const profileVisible = ref(false)
// 我进行中的直播间（主播刷新浏览器后一键回到直播间）
const livingRoomId = ref(null)
async function refreshMyLivingRoom() {
  try {
    const vo = await myLivingRoom()
    livingRoomId.value = vo.data || null
  } catch { /* 静默 */ }
}
const startDialogRef = ref(null)
async function handleStartLiving({ roomName, covertImg, type }) {
  try {
    const vo = await startLiving(type || 1, roomName, covertImg)
    const newRoomId = vo.data?.roomId
    if (newRoomId) {
      startDialogRef.value?.finish()
      router.push(`/room/${newRoomId}`)
    } else {
      ElMessage.error('开播失败')
      startDialogRef.value?.fail()
    }
  } catch {
    ElMessage.error('开播失败')
    startDialogRef.value?.fail()
  }
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  userStore.refreshBalance()
  await fetchRooms()
  refreshMyLivingRoom()
})
</script>

<style scoped>
.home-page { min-height: 100vh; background: #0a0a0a; }
.nav-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 32px; background: #161625; border-bottom: 1px solid #222;
}
.logo { font-size: 20px; font-weight: bold; color: #667eea; }
.nav-tab {
  font-size: 15px; color: #888; cursor: pointer; padding: 4px 6px;
  border-radius: 6px; transition: color 0.2s;
}
.nav-tab:hover { color: #ddd; }
.nav-tab.active { color: #fff; font-weight: bold; }
.nav-right { display: flex; align-items: center; gap: 12px; }
.avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
.nickname { color: #ddd; font-size: 14px; }
.balance-chip {
  display: inline-flex; align-items: center; gap: 4px;
  background: linear-gradient(135deg, #3a2c00, #4a3a00);
  border: 1px solid #7a5c00; color: #ffd700;
  font-size: 13px; font-weight: bold;
  padding: 4px 12px; border-radius: 16px; cursor: pointer;
  transition: all 0.2s;
}
.balance-chip:hover { border-color: #ffd700; }
.coin-icon { font-size: 13px; }
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
