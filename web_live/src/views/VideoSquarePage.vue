<template>
  <div class="video-square">
    <header class="nav-bar">
      <div class="nav-left">
        <span class="logo">🎬 旗鱼直播</span>
        <span class="nav-tab" @click="$router.push('/')">直播</span>
        <span class="nav-tab active">视频</span>
      </div>
      <div class="nav-right">
        <template v-if="userStore.userInfo.loginStatus">
          <img :src="userStore.userInfo.avatar || defaultAvatar" class="avatar" />
          <span class="nickname">{{ userStore.userInfo.nickName }}</span>
          <span class="balance-chip" @click="$router.push('/wallet')" title="去充值">
            <span class="coin-icon">🪙</span>{{ formatBalance(userStore.balance) }}
          </span>
          <el-button size="small" type="danger" @click="$router.push('/')">直播间</el-button>
          <el-button size="small" type="danger" @click="handleLogout">退出</el-button>
        </template>
        <el-button size="small" type="primary" v-else @click="$router.push('/login')">登录</el-button>
      </div>
    </header>

    <!-- 标签筛选 + 发布 -->
    <div class="filter-bar">
      <span
        v-for="t in tags"
        :key="t.id"
        :class="['type-tag', { active: currentTag === t.id }]"
        @click="switchTag(t.id)"
      >{{ t.tagName }}</span>
      <div class="filter-spacer"></div>
      <el-button type="danger" round @click="publishVisible = true">⬆ 发布视频</el-button>
    </div>

    <!-- 视频卡片流 -->
    <div class="video-grid">
      <div v-for="v in videos" :key="v.id" class="video-card" @click="$router.push(`/video/${v.id}`)">
        <div class="cover-wrap">
          <img :src="v.coverUrl || defaultCover" class="cover" />
          <span class="duration" v-if="v.duration">{{ formatDuration(v.duration) }}</span>
          <span class="play-count">▶ {{ formatCount(v.playCount) }}</span>
        </div>
        <div class="video-info">
          <div class="video-title">{{ v.title }}</div>
          <div class="video-meta">
            <span class="author">{{ v.nickName || '未知用户' }}</span>
            <span class="likes">👍 {{ formatCount(v.likeCount) }}</span>
          </div>
        </div>
      </div>
      <div v-if="videos.length === 0" class="empty">暂无视频，快来发布第一个吧</div>
    </div>

    <!-- 发布弹窗 -->
    <VideoPublishDialog v-model="publishVisible" @published="onPublished" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listVideos, listVideoTags } from '@/api/video'
import VideoPublishDialog from '@/components/VideoPublishDialog.vue'

const router = useRouter()
const userStore = useUserStore()
const videos = ref([])
const tags = ref([])
const currentTag = ref(0)
const publishVisible = ref(false)

const defaultAvatar = 'https://via.placeholder.com/36/667eea/fff?text=U'
const defaultCover = 'https://via.placeholder.com/320x180/1a1a2e/667eea?text=Video'

const formatBalance = (n) => (Number(n) || 0).toLocaleString()
const formatCount = (n) => {
  n = Number(n) || 0
  return n >= 10000 ? (n / 10000).toFixed(1) + 'w' : String(n)
}
const formatDuration = (s) => {
  const m = Math.floor(s / 60), r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}

async function fetchVideos() {
  const vo = await listVideos(currentTag.value, 1, 30)
  videos.value = vo.data || []
}

async function switchTag(tagId) {
  if (currentTag.value === tagId) return
  currentTag.value = tagId
  await fetchVideos()
}

function onPublished(videoId) {
  publishVisible.value = false
  router.push(`/video/${videoId}`)
}

async function handleLogout() {
  await userStore.logoutUser()
  router.push('/')
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  userStore.refreshBalance()
  try {
    const vo = await listVideoTags()
    tags.value = vo.data || []
  } catch { /* 标签加载失败不阻塞列表 */ }
  await fetchVideos()
})
</script>

<style scoped>
.video-square { min-height: 100vh; background: var(--sq-abyss); color: #fff; }
.nav-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 32px; background: var(--sq-deep); border-bottom: 1px solid var(--sq-line);
}
.nav-left { display: flex; align-items: center; gap: 18px; }
.logo { font-size: 20px; font-weight: bold; color: var(--sq-blue); }
.nav-tab { font-size: 15px; color: #888; cursor: pointer; padding: 4px 6px; border-radius: 6px; }
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
}
.coin-icon { font-size: 13px; }

.filter-bar { display: flex; align-items: center; gap: 14px; padding: 20px 32px; }
.filter-spacer { flex: 1; }
.type-tag {
  padding: 6px 18px; border-radius: 20px; cursor: pointer; font-size: 14px;
  color: #888; background: var(--sq-card); transition: all 0.2s;
}
.type-tag.active { background: var(--sq-blue); color: #fff; }

.video-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px; padding: 0 32px 40px;
}
.video-card { background: var(--sq-deep); border-radius: 12px; overflow: hidden; cursor: pointer; transition: transform 0.2s; }
.video-card:hover { transform: translateY(-4px); }
.cover-wrap { position: relative; }
.cover { width: 100%; height: 150px; object-fit: cover; display: block; background: var(--sq-card); }
.duration {
  position: absolute; bottom: 8px; right: 8px;
  background: rgba(0,0,0,0.7); color: #fff; font-size: 12px;
  padding: 1px 6px; border-radius: 4px;
}
.play-count {
  position: absolute; bottom: 8px; left: 8px;
  background: rgba(0,0,0,0.7); color: #fff; font-size: 12px;
  padding: 1px 6px; border-radius: 4px;
}
.video-info { padding: 12px; }
.video-title { font-size: 14px; color: #ddd; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.video-meta { display: flex; justify-content: space-between; font-size: 12px; color: #666; }
.author { color: var(--sq-blue); }
.empty { grid-column: 1/-1; text-align: center; color: #444; padding: 60px 0; }
</style>
