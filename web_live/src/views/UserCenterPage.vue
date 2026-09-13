<template>
  <div class="user-center">
    <header class="nav-bar">
      <span class="back-btn" @click="$router.push('/')">← 返回</span>
      <span class="title">个人中心</span>
      <span class="user-chip">
        <img :src="userStore.userInfo.avatar || defaultAvatar" class="avatar" />
        {{ userStore.userInfo.nickName }}
      </span>
    </header>

    <div class="content">
      <div class="tab-row">
        <span
          v-for="t in tabs"
          :key="t.key"
          :class="['tab-item', { active: currentTab === t.key }]"
          @click="switchTab(t.key)"
        >{{ t.label }}</span>
      </div>

      <div v-loading="loading" class="video-grid">
        <div
          v-for="v in list"
          :key="v.id"
          class="video-card"
          @click="$router.push(`/video/${v.id}`)"
        >
          <div class="cover-wrap">
            <img :src="v.coverUrl || defaultCover" class="cover" />
            <span v-if="v.duration" class="duration">{{ fmtDuration(v.duration) }}</span>
            <span class="play-count">▶ {{ fmtCount(v.playCount) }}</span>
            <span v-if="currentTab === 'history' && v.watchedAt" class="watched-at">{{ v.watchedAt }}</span>
          </div>
          <div class="video-info">
            <div class="video-title">{{ v.title }}</div>
            <div class="video-meta">
              <span class="author">{{ v.nickName || '我' }}</span>
              <span class="likes">👍 {{ fmtCount(v.likeCount) }}</span>
            </div>
          </div>
        </div>
        <div v-if="!loading && list.length === 0" class="empty">{{ emptyText[currentTab] }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { myHistory, myList, myFavorites, myLikes } from '@/api/video'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const defaultAvatar = 'https://via.placeholder.com/36/667eea/fff?text=U'
const defaultCover = 'https://via.placeholder.com/320x180/1a1a2e/667eea?text=Video'

const tabs = [
  { key: 'my', label: '我的视频' },
  { key: 'favorites', label: '我的收藏' },
  { key: 'likes', label: '我点赞的' },
  { key: 'history', label: '观看历史' },
]
const emptyText = {
  my: '还没有发布过视频，去发布第一个吧',
  favorites: '还没有收藏视频',
  likes: '还没有点赞视频',
  history: '还没有观看记录',
}
const currentTab = ref('my')
const list = ref([])
const loading = ref(false)

const fmtCount = (n) => {
  n = Number(n) || 0
  return n >= 10000 ? (n / 10000).toFixed(1) + 'w' : String(n)
}
const fmtDuration = (s) => {
  const m = Math.floor(s / 60), r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}

// 历史接口返回带 watchedAt（后端把观看时间塞进 remark 字段或单独字段时直接展示）
const fetchList = async () => {
  loading.value = true
  try {
    const fn = { my: myList, favorites: myFavorites, likes: myLikes, history: myHistory }[currentTab.value]
    const { data } = await fn(1, 30)
    list.value = data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  if (currentTab.value === key) return
  currentTab.value = key
}

watch(currentTab, fetchList)
onMounted(fetchList)
</script>

<style scoped>
.user-center { min-height: 100vh; background: var(--sq-abyss); color: #fff; }
.nav-bar {
  display: flex; align-items: center; gap: 16px;
  padding: 16px 32px; background: var(--sq-deep); border-bottom: 1px solid var(--sq-line);
}
.back-btn { cursor: pointer; color: #999; }
.back-btn:hover { color: #fff; }
.title { font-size: 18px; font-weight: bold; flex: 1; }
.user-chip { display: flex; align-items: center; gap: 8px; color: #ddd; font-size: 14px; }
.avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }

.content { max-width: 1200px; margin: 0 auto; padding: 20px 24px; }
.tab-row { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }
.tab-item {
  padding: 8px 20px; border-radius: 20px; cursor: pointer; font-size: 14px;
  color: #888; background: var(--sq-card); transition: all 0.2s;
}
.tab-item:hover { color: #ddd; }
.tab-item.active { background: var(--sq-blue); color: #fff; }

.video-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px; min-height: 200px;
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
.watched-at {
  position: absolute; top: 8px; right: 8px;
  background: rgba(102, 126, 234, 0.85); color: #fff; font-size: 11px;
  padding: 1px 6px; border-radius: 4px;
}
.video-info { padding: 12px; }
.video-title { font-size: 14px; color: #ddd; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.video-meta { display: flex; justify-content: space-between; font-size: 12px; color: #666; }
.author { color: var(--sq-blue); }
.empty { grid-column: 1/-1; text-align: center; color: #444; padding: 60px 0; }
</style>
