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

      <!-- 主播看板 -->
      <div v-if="currentTab === 'dashboard'" class="dash-board" v-loading="dashLoading">
        <div class="dash-tip" v-if="!dash.roomId">当前未在直播；开播后数据实时更新，再次打开本页可刷新。</div>
        <template v-else>
          <div class="dash-cards">
            <div class="dash-card"><div class="lbl">本场人气（累计进房人次）</div><div class="val">{{ dash.heat }}</div></div>
            <div class="dash-card"><div class="lbl">当前在线</div><div class="val">{{ dash.online }}</div></div>
            <div class="dash-card"><div class="lbl">本场收礼（金币）</div><div class="val">{{ dash.giftCoins }}</div></div>
            <div class="dash-card"><div class="lbl">直播状态</div><div class="val">{{ dash.streamStatus === 1 ? '推流中' : '待推流' }}</div></div>
          </div>
          <div class="dash-contrib" v-if="dash.contrib.length">
            <div class="sub-title">本场贡献 Top10</div>
            <div class="contrib-row" v-for="c in dash.contrib" :key="c.userId">
              <span class="no">{{ c.rank }}</span>
              <img :src="c.avatar || defaultCover" class="avt" />
              <span class="nick">{{ c.nickName || ('用户' + c.userId) }}</span>
              <span class="score">🪙 {{ c.score }}</span>
            </div>
          </div>
        </template>
      </div>
      <div v-else v-loading="loading" class="video-grid">
        <!-- 📊 创作看板 -->

        <div v-if="currentTab === 'creator'" class="creator-board">

          <template v-if="creatorStats">

            <div class="stat-cards">

              <div class="stat-card"><b>{{ creatorStats.videoCount }}</b><span>视频</span></div>

              <div class="stat-card"><b>{{ creatorStats.playCount }}</b><span>播放</span></div>

              <div class="stat-card"><b>{{ creatorStats.likeCount }}</b><span>点赞</span></div>

              <div class="stat-card"><b>{{ creatorStats.favoriteCount }}</b><span>收藏</span></div>

              <div class="stat-card"><b>{{ creatorStats.commentCount }}</b><span>评论</span></div>

            </div>

            <div class="stat-title">播放 Top5</div>

            <div v-for="(v, i) in creatorStats.top" :key="v.id" class="top-row" @click="$router.push(`/video/${v.id}`)">

              <span class="top-rank" :class="'top-' + (i + 1)">{{ i + 1 }}</span>

              <span class="top-name">{{ v.title }}</span>

              <span class="top-count">▶ {{ v.playCount }}</span>

            </div>

            <div v-if="!creatorStats.videoCount" class="empty">还没有发布过视频</div>

          </template>

        </div>


        <div v-if="currentTab !== 'creator'"
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
import { ref, reactive, onMounted, watch } from 'vue'
import { myHistory, myList, myFavorites, myLikes } from '@/api/video'
import { useUserStore } from '@/stores/user'
import { myLivingRoom, onlineCount } from '@/api/room'
import { roomGiftRank, heatRank } from '@/api/rank'

const userStore = useUserStore()
const defaultAvatar = 'https://via.placeholder.com/36/667eea/fff?text=U'
const defaultCover = 'https://via.placeholder.com/320x180/1a1a2e/667eea?text=Video'

const tabs = [
  { key: 'my', label: '我的视频' },
  { key: 'creator', label: '📊 创作看板' },
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

// 创作看板：汇总我的视频数据
const creatorStats = ref(null)
async function loadCreatorStats() {
  loading.value = true
  try {
    const { data } = await myList(1, 50)
    const list = data || []
    creatorStats.value = {
      videoCount: list.length,
      playCount: list.reduce((s, v) => s + (Number(v.playCount) || 0), 0),
      likeCount: list.reduce((s, v) => s + (Number(v.likeCount) || 0), 0),
      favoriteCount: list.reduce((s, v) => s + (Number(v.favoriteCount) || 0), 0),
      commentCount: list.reduce((s, v) => s + (Number(v.commentCount) || 0), 0),
      top: [...list].sort((a, b) => (Number(b.playCount) || 0) - (Number(a.playCount) || 0)).slice(0, 5),
    }
  } catch {
    creatorStats.value = null
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  if (currentTab.value === key) return
  currentTab.value = key
}

watch(currentTab, (k) => { if (k === 'creator') loadCreatorStats(); else fetchList() })
onMounted(fetchList)
// ==================== 主播看板 ====================
const dash = reactive({ roomId: null, heat: 0, online: 0, giftCoins: 0, streamStatus: 0, contrib: [] })
const dashLoading = ref(false)

async function loadDashboard() {
  dashLoading.value = true
  try {
    const my = await myLivingRoom()
    dash.roomId = my.data ? Number(my.data) : null
    if (!dash.roomId) return
    const heatVo = await heatRank()
    const heatItem = (heatVo.data || []).find(r => Number(r.roomId) === dash.roomId)
    dash.heat = heatItem ? heatItem.score : 0
    const onlineVo = await onlineCount(dash.roomId)
    dash.online = Number(onlineVo.data) || 0
    const giftVo = await roomGiftRank(dash.roomId)
    dash.contrib = giftVo.data || []
    dash.giftCoins = dash.contrib.reduce((sum, c) => sum + Number(c.score || 0), 0)
  } catch { /* 静默 */ } finally {
    dashLoading.value = false
  }
}

watch(currentTab, (t) => { if (t === 'dashboard') loadDashboard() })
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

<style scoped>
.dash-board { padding: 8px 0; }
.dash-tip { color: #888; font-size: 13px; }
.dash-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.dash-card { background: var(--sq-card, #1a1f2b); border-radius: 10px; padding: 16px; }
.dash-card .lbl { font-size: 12px; color: #888; }
.dash-card .val { font-size: 24px; font-weight: bold; margin-top: 6px; color: #fff; }
.dash-contrib { margin-top: 20px; }
.sub-title { font-size: 14px; color: #ccc; margin-bottom: 8px; }
.contrib-row { display: flex; align-items: center; gap: 10px; padding: 6px 0; }
.contrib-row .no { width: 20px; color: #888; }
.contrib-row .avt { width: 26px; height: 26px; border-radius: 50%; object-fit: cover; }
.contrib-row .nick { flex: 1; font-size: 13px; color: #ddd; }
.contrib-row .score { color: #e6a23c; font-size: 13px; }
.creator-board { display: flex; flex-direction: column; gap: 12px; }
.stat-cards { display: flex; gap: 10px; flex-wrap: wrap; }
.stat-card { flex: 1; min-width: 90px; background: var(--sq-card, #fff); border-radius: 10px; padding: 14px 10px; text-align: center; display: flex; flex-direction: column; }
.stat-card b { font-size: 22px; color: var(--sq-blue, #409eff); }
.stat-card span { font-size: 12px; color: #888; }
.stat-title { font-weight: 600; margin-top: 6px; }
.top-row { display: flex; align-items: center; gap: 10px; background: var(--sq-card, #fff); border-radius: 8px; padding: 10px 12px; cursor: pointer; }
.top-rank { width: 22px; height: 22px; border-radius: 50%; background: #ddd; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; }
.top-1 { background: #f44336; } .top-2 { background: #ff9800; } .top-3 { background: #ffc107; }
.top-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top-count { color: #999; font-size: 12px; }
</style>
