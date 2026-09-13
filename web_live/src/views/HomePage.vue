<template>
  <div class="home-page">
    <!-- 顶部导航 -->
    <header class="nav-bar">
      <div class="nav-left">
        <span class="logo">🎬 旗鱼直播</span>
        <span :class="['nav-tab', { active: $route.path === '/' }]" @click="$router.push('/')">直播</span>
        <span :class="['nav-tab', { active: $route.path.startsWith('/video') }]" @click="$router.push('/video')">视频</span>
        <span
          v-if="userStore.userInfo.loginStatus"
          :class="['nav-tab', { active: $route.path === '/messages' }]"
          @click="$router.push('/messages')"
        >消息<span v-if="dmUnread > 0" class="nav-badge">{{ dmUnread > 99 ? '99+' : dmUnread }}</span></span>
      </div>
      <div class="nav-right">
        <template v-if="userStore.userInfo.loginStatus">
          <el-dropdown trigger="click" @command="handleAvatarCommand">
            <img :src="userStore.userInfo.avatar || defaultAvatar" class="avatar" title="个人菜单" style="cursor: pointer" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="dark">深色/浅色</el-dropdown-item>
                <el-dropdown-item command="center">个人中心</el-dropdown-item>
                <el-dropdown-item command="myprofile">我的主页</el-dropdown-item>
                <el-dropdown-item command="profile">个人设置</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="nickname">{{ userStore.userInfo.nickName }}</span>
          <span class="balance-chip" @click="$router.push('/wallet')" title="去充值">
            <span class="coin-icon">🪙</span>{{ formatBalance }}
          </span>
          <el-button v-if="userStore.userInfo.showStartLivingBtn" size="small" @click="shopManageVisible = true">商品</el-button>
          <el-button v-if="userStore.userInfo.showStartLivingBtn" size="small" class="start-btn" @click="startVisible = true">开播</el-button>
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
    <div class="room-grid" v-if="loading">
      <el-skeleton v-for="i in 8" :key="i" class="room-skeleton" animated>
        <template #template>
          <el-skeleton-item variant="image" style="width: 100%; height: 140px" />
          <div style="padding: 10px"><el-skeleton-item variant="text" style="width: 60%" /></div>
        </template>
      </el-skeleton>
    </div>
    <div class="room-grid" v-else>
      <div v-for="room in rooms" :key="room.id" class="room-card" @click="$router.push(`/room/${room.id}`)">
        <div class="cover-wrap">
          <img :src="room.covertImg || defaultCover" class="cover" loading="lazy" />
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
    <StartLivingDialog ref="startDialogRef" v-model="startVisible" @confirm="handleStartLiving" @goShopManage="shopManageVisible = true" />
    <ShopManageDialog v-model="shopManageVisible" />
    <UserProfileDialog v-model="profileVisible" />
  </div>
  <!-- 排行榜弹层 -->
  <el-dialog v-model="rankVisible" title="🏆 排行榜" width="480px" class="rank-dialog">
    <div class="rank-tabs">
      <span :class="['rank-tab', { active: rankTab === 'day' }]" @click="switchRank('day')">收礼日榜</span>
      <span :class="['rank-tab', { active: rankTab === 'week' }]" @click="switchRank('week')">收礼周榜</span>
      <span :class="['rank-tab', { active: rankTab === 'heat' }]" @click="switchRank('heat')">人气榜</span>
    </div>
    <div class="rank-list">
      <div class="rank-row" v-for="item in rankList" :key="item.rank">
        <span :class="['rank-no', { top1: item.rank === 1, top2: item.rank === 2, top3: item.rank === 3 }]">{{ item.rank }}</span>
        <img v-if="rankTab !== 'heat'" :src="item.avatar || defaultAvatar" class="rank-avatar" />
        <span class="rank-name" v-if="rankTab !== 'heat'" @click="item.userId && $router.push(`/profile/${item.userId}`)">
          {{ item.nickName || ('用户' + item.userId) }}</span>
        <span class="rank-name" v-else @click="item.roomId && $router.push(`/room/${item.roomId}`)">{{ item.roomName }}</span>
        <span class="rank-score">{{ rankTab === 'heat' ? '🔥 ' + (item.score || 0) : '🪙 ' + (item.score || 0) }}</span>
      </div>
      <div v-if="rankList.length === 0" class="rank-empty">暂无数据，快去播种吧~</div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listRoom, startLiving, myLivingRoom } from '@/api/room'
import { anchorGiftRank, heatRank } from '@/api/rank'
import { notifyList, notifyRead, notifyUnreadCount } from '@/api/user'
import { dmUnreadTotal } from '@/api/dm'
import StartLivingDialog from '@/components/StartLivingDialog.vue'
import UserProfileDialog from '@/components/UserProfileDialog.vue'
import ShopManageDialog from '@/components/ShopManageDialog.vue'
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

const loading = ref(false)
async function fetchRooms() {
  loading.value = true
  try {
    const vo = await listRoom({ type: currentType.value, page: 1, pageSize: 20 })
    rooms.value = vo.data?.list || []
  } finally {
    loading.value = false
  }
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
const shopManageVisible = ref(false)
// 我进行中的直播间（主播刷新浏览器后一键回到直播间）
const livingRoomId = ref(null)

// ==================== 搜索 / 排行榜 / 通知中心 ====================
const searchKeyword = ref('')
function goSearch() {
  if (searchKeyword.value.trim()) router.push(`/search?keyword=${encodeURIComponent(searchKeyword.value.trim())}`)
}

const rankVisible = ref(false)
const rankTab = ref('day')
const rankList = ref([])
async function switchRank(tab) {
  rankTab.value = tab
  try {
    const vo = tab === 'heat' ? await heatRank() : await anchorGiftRank(tab)
    rankList.value = vo.data || []
  } catch { rankList.value = [] }
}

const notifyUnread = ref(0)
// 私信未读（60s 轮询，进 /messages 清零后由下次轮询修正）
const dmUnread = ref(0)
let dmUnreadTimer = null
async function refreshDmUnread() {
  if (!userStore.userInfo.loginStatus) return
  try {
    const vo = await dmUnreadTotal()
    dmUnread.value = Number(vo.data) || 0
  } catch { /* 忽略 */ }
}
const notifyListData = ref([])
async function refreshUnread() {
  if (!userStore.userInfo.loginStatus) return
  try {
    const vo = await notifyUnreadCount()
    notifyUnread.value = Number(vo.data) || 0
  } catch { /* 忽略 */ }
}
async function loadNotifyList() {
  try {
    const vo = await notifyList(1, 20)
    notifyListData.value = vo.data?.list || []
  } catch { notifyListData.value = [] }
}
async function handleNotifyCommand(n) {
  if (n.jumpUrl) router.push(n.jumpUrl)
  if (!n.isRead) {
    await notifyRead(n.id)
    refreshUnread()
  }
}
async function markAllRead() {
  await notifyRead()
  refreshUnread()
  notifyListData.value = notifyListData.value.map(n => ({ ...n, isRead: 1 }))
}
async function handleAvatarCommand(cmd) {
  if (cmd === 'dark') {
    const el = document.documentElement
    const dark = !el.classList.contains('dark')
    el.classList.toggle('dark', dark)
    localStorage.setItem('qiyu_dark', dark ? '1' : '0')
    return
  }
  if (cmd === 'center') router.push('/user/center')
  else if (cmd === 'myprofile') router.push('/profile')
  else if (cmd === 'profile') profileVisible.value = true
}

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
  refreshUnread()
  refreshDmUnread()
  dmUnreadTimer = setInterval(refreshDmUnread, 60000)
})
onUnmounted(() => clearInterval(dmUnreadTimer))
</script>

<style scoped>
.room-skeleton { background: var(--sq-deep); border-radius: 12px; overflow: hidden; }
.home-page { min-height: 100vh; background: var(--sq-abyss); }
.nav-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 32px; background: var(--sq-deep); border-bottom: 1px solid var(--sq-line);
}
.logo { font-size: 20px; font-weight: bold; font-family: var(--sq-font-display); letter-spacing: 0.5px; color: var(--sq-blue); }
.nav-tab {
  font-size: 15px; color: #888; cursor: pointer; padding: 4px 6px;
  border-radius: 6px; transition: color 0.2s;
}
.nav-tab:hover { color: #ddd; }
.nav-tab.active { color: #fff; font-weight: bold; }
.nav-badge {
  display: inline-block; margin-left: 4px; background: #f56c6c; color: #fff;
  font-size: 10px; border-radius: 8px; padding: 0 5px; vertical-align: top;
}
.nav-right { display: flex; align-items: center; gap: 12px; }
.avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
.nickname { color: #ddd; font-size: 14px; }
.balance-chip { font-family: var(--sq-font-mono);
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
  color: #888; background: var(--sq-card); transition: all 0.2s;
}
.type-tag.active { background: var(--sq-blue); color: #fff; }
.room-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px; padding: 0 32px 40px;
}
.room-card { background: var(--sq-deep); border-radius: 12px; overflow: hidden; cursor: pointer; transition: transform 0.2s; }
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

<style>
/* 高能动作专用渐变按钮（开播） */
.start-btn {
  border: none !important;
  background: var(--sq-energetic) !important;
  color: #fff !important;
  font-weight: bold;
}
.start-btn:hover { filter: brightness(1.12); }

/* 搜索 / 排行榜 / 通知 */
.global-search {
  width: 220px; padding: 7px 12px; border-radius: 6px; border: 1px solid #2a3040;
  background: rgba(255,255,255,0.06); color: #fff; outline: none; font-size: 13px;
}
.global-search:focus { border-color: #667eea; }
.global-search::placeholder { color: #666; }
.rank-entry { cursor: pointer; font-size: 18px; }
.bell { cursor: pointer; font-size: 16px; position: relative; outline: none; }
.bell-badge {
  position: absolute; top: -6px; right: -8px; background: #f56c6c; color: #fff;
  font-size: 10px; line-height: 14px; padding: 0 4px; border-radius: 7px;
}
.notify-header {
  display: flex; justify-content: space-between; padding: 8px 12px;
  font-weight: bold; border-bottom: 1px solid #2a3040;
}
.notify-readall { color: #667eea; font-size: 12px; cursor: pointer; font-weight: normal; }
.notify-empty { padding: 20px; text-align: center; color: #888; font-size: 13px; }
.notify-item { line-height: 1.4; }
.notify-title { font-size: 13px; font-weight: bold; }
.notify-content { font-size: 12px; color: #888; max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notify-unread .notify-title::before { content: "●"; color: #f56c6c; font-size: 10px; margin-right: 4px; }
.rank-tabs { display: flex; gap: 18px; margin-bottom: 14px; border-bottom: 1px solid #2a3040; }
.rank-tab { cursor: pointer; color: #888; padding-bottom: 8px; }
.rank-tab.active { color: #667eea; font-weight: bold; border-bottom: 2px solid #667eea; }
.rank-row { display: flex; align-items: center; gap: 12px; padding: 8px 6px; }
.rank-no { width: 24px; text-align: center; font-weight: bold; color: #888; }
.rank-no.top1 { color: #ffd700; }
.rank-no.top2 { color: #c0c0c0; }
.rank-no.top3 { color: #cd7f32; }
.rank-avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.rank-name { flex: 1; font-size: 14px; cursor: pointer; }
.rank-name:hover { color: #667eea; }
.rank-score { color: #e6a23c; font-size: 13px; }
.rank-empty { text-align: center; color: #888; padding: 32px 0; }
</style>