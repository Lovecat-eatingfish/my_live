<template>
  <div class="feed-page">
    <!-- 顶部悬浮导航 -->
    <header class="feed-nav">
      <div class="nav-left">
        <span class="logo" @click="$router.push('/')">🎬 旗鱼直播</span>
        <span class="nav-tab" @click="$router.push('/')">直播</span>
        <span class="nav-tab active">视频</span>
      </div>
      <div class="nav-right">
        <el-button size="small" type="primary" round @click="publishVisible = true">⬆ 发布</el-button>
        <template v-if="userStore.userInfo.loginStatus">
          <span class="balance-chip" @click="$router.push('/wallet')" title="去充值">
            <span class="coin-icon">🪙</span>{{ formatBalance(userStore.balance) }}
          </span>
          <img :src="userStore.userInfo.avatar || defaultAvatar" class="avatar"
            @click="$router.push('/profile')" title="我的主页" />
        </template>
        <el-button size="small" v-else @click="$router.push('/login')">登录</el-button>
      </div>
    </header>

    <!-- 沉浸式竖屏 Feed：scroll-snap 逐屏吸附 -->
    <div class="feed-scroll" ref="scrollRef">
      <div class="feed-item" v-for="(v, idx) in videos" :key="v.id">
        <video
          :ref="el => setVideoRef(el, idx)"
          class="feed-video"
          :src="v.videoUrl"
          :poster="v.coverUrl || ''"
          loop
          muted
          playsinline
          preload="auto"
          @ended="onEnded(v, $event)"
          @dblclick.prevent="doubleClickLike(v, $event)"
          @click="togglePlay(v, idx)"
        ></video>

        <!-- 暂停遮罩 -->
        <div class="pause-mask" v-if="pausedSet.has(v.id)">
          <span class="play-icon">▶</span>
        </div>

        <!-- 双击点赞爱心 -->
        <div class="heart-layer">
          <span class="heart" v-for="h in (hearts[v.id] || [])" :key="h.id"
            :style="{ left: h.x + '%', top: h.y + '%' }">❤️</span>
        </div>

        <!-- 右侧悬浮按钮列 -->
        <div class="action-col">
          <div class="action-item" @click="$router.push(`/profile/${v.userId}`)">
            <img :src="v.avatar || defaultAvatar" class="action-avatar" />
          </div>
          <div class="action-item" @click="doLike(v)">
            <span class="action-icon">{{ v.liked ? '❤️' : '🤍' }}</span>
            <span class="action-num">{{ formatCount(v.likeCount) }}</span>
          </div>
          <div class="action-item" @click="openComments(v)">
            <span class="action-icon">💬</span>
            <span class="action-num">{{ formatCount(v.commentCount) }}</span>
          </div>
          <div class="action-item" @click="doFavorite(v)">
            <span class="action-icon">{{ v.favorited ? '⭐' : '☆' }}</span>
            <span class="action-num">{{ formatCount(v.favoriteCount) }}</span>
          </div>
          <div class="action-item" @click="doShare(v)">
            <span class="action-icon">↗️</span>
            <span class="action-num">{{ formatCount(v.shareCount) }}</span>
          </div>
        </div>

        <!-- 左下角信息 -->
        <div class="info-area">
          <div class="info-title">{{ v.title }}</div>
          <div class="info-author">@{{ v.nickName || '未知用户' }}</div>
        </div>

        <!-- 静音切换 -->
        <div class="mute-toggle" @click.stop="muted = !muted">{{ muted ? '🔇' : '🔊' }}</div>
      </div>
      <div v-if="videos.length === 0" class="feed-empty">暂无视频，点击右上角发布第一个吧</div>
    </div>

    <!-- 评论底部抽屉 -->
    <el-drawer v-model="commentVisible" direction="btt" size="45%" :with-header="false" class="comment-drawer">
      <div class="comment-panel" v-if="currentVideo">
        <div class="comment-head">💬 评论 {{ formatCount(currentVideo.commentCount) }}</div>
        <div class="comment-list">
          <div class="comment-row" v-for="c in comments" :key="c.id">
            <img :src="c.avatar || defaultAvatar" class="comment-avatar" />
            <div class="comment-body">
              <div class="comment-name">{{ c.nickName || '用户' }}</div>
              <div class="comment-content">{{ c.content }}</div>
            </div>
          </div>
          <div v-if="comments.length === 0" class="comment-empty">还没有评论，来说两句~</div>
        </div>
        <div class="comment-input-row">
          <input v-model="commentText" class="comment-input" placeholder="说点什么..."
            @keyup.enter="sendComment" />
          <el-button size="small" type="primary" :disabled="!commentText.trim()" @click="sendComment">发送</el-button>
        </div>
      </div>
    </el-drawer>

    <VideoPublishDialog v-model="publishVisible" @published="onPublished" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { feedVideos, likeVideo, favoriteVideo, shareVideo, listComments, addComment, playReport } from '@/api/video'
import VideoPublishDialog from '@/components/VideoPublishDialog.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const videos = ref([])
const publishVisible = ref(false)
const muted = ref(true)
const scrollRef = ref(null)
const pausedSet = reactive(new Set())

const defaultAvatar = 'https://via.placeholder.com/44/667eea/fff?text=U'

// 视频元素引用与可见性观察器
const videoEls = []
let observer = null
// 每条视频已上报标记（离开/结束时上报一次）
const watchedMap = reactive({})

const formatBalance = (n) => (Number(n) || 0).toLocaleString()
const formatCount = (n) => {
  n = Number(n) || 0
  return n >= 10000 ? (n / 10000).toFixed(1) + 'w' : String(n)
}

function setVideoRef(el, idx) {
  if (el) videoEls[idx] = el
}

// ---- 数据加载：游标分页 ----
async function loadFeed(reset = false) {
  const lastId = reset || videos.value.length === 0 ? null : videos.value[videos.value.length - 1].id
  const vo = await feedVideos(lastId, 10)
  const list = vo.data || []
  if (reset) {
    videos.value = list
  } else {
    const exist = new Set(videos.value.map(v => v.id))
    videos.value.push(...list.filter(v => !exist.has(v.id)))
  }
  if (list.length > 0) {
    nextTick(() => observer && videos.value.forEach((v, i) => {
      if (videoEls[i]) observer.observe(videoEls[i])
    }))
  }
}

// ---- 可见性驱动播放（threshold 0.6）----
function setupObserver() {
  observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      const el = entry.target
      const idx = videoEls.indexOf(el)
      const v = videos.value[idx]
      if (!v) return
      if (entry.isIntersecting && entry.intersectionRatio >= 0.6) {
        el.muted = muted.value
        el.play().catch(() => {})
        pausedSet.delete(v.id)
        // 接近底部预取下一页
        if (idx >= videos.value.length - 3) loadFeed()
      } else {
        el.pause()
        reportWatched(v, el)
        pausedSet.add(v.id)
      }
    })
  }, { threshold: [0, 0.6, 1] })
}

function reportWatched(v, el) {
  const watched = Math.floor(el.currentTime || 0)
  if (!watchedMap[v.id] && watched >= 3) {
    watchedMap[v.id] = true
    playReport(v.id, watched, v.duration || 0).catch(() => {})
  }
}

function onEnded(v, e) {
  // 完播：整段看完，按视频时长上报
  playReport(v.id, v.duration || Math.floor(e.target.currentTime || 0), v.duration || 0).catch(() => {})
  watchedMap[v.id] = true
}

function togglePlay(v, idx) {
  const el = videoEls[idx]
  if (!el) return
  if (el.paused) {
    el.play().catch(() => {})
    pausedSet.delete(v.id)
  } else {
    el.pause()
    pausedSet.add(v.id)
  }
}

// ---- 互动 ----
async function doLike(v) {
  const vo = await likeVideo(v.id, !v.liked)
  v.liked = !!vo.data
  v.likeCount = Number(v.likeCount || 0) + (v.liked ? 1 : -1)
}

async function doFavorite(v) {
  const vo = await favoriteVideo(v.id, !v.favorited)
  v.favorited = !!vo.data
  v.favoriteCount = Number(v.favoriteCount || 0) + (v.favorited ? 1 : -1)
}

async function doShare(v) {
  await shareVideo(v.id)
  v.shareCount = Number(v.shareCount || 0) + 1
  try { await navigator.clipboard.writeText(location.origin + `/video/${v.id}`); ElMessage.success('链接已复制') } catch { }
}

// 双击点赞 + 爱心动画
const hearts = reactive({})
async function doubleClickLike(v, e) {
  if (!v.liked) await doLike(v)
  if (!hearts[v.id]) hearts[v.id] = []
  const rect = e.target.getBoundingClientRect()
  hearts[v.id].push({
    id: Date.now() + Math.random(),
    x: ((e.clientX - rect.left) / rect.width) * 100,
    y: ((e.clientY - rect.top) / rect.height) * 100
  })
  setTimeout(() => { hearts[v.id] = hearts[v.id].slice(1) }, 900)
}

// ---- 评论底部抽屉 ----
const commentVisible = ref(false)
const currentVideo = ref(null)
const comments = ref([])
const commentText = ref('')

async function openComments(v) {
  currentVideo.value = v
  commentVisible.value = true
  const vo = await listComments(v.id, 1, 50)
  comments.value = vo.data || []
}

async function sendComment() {
  const text = commentText.value.trim()
  if (!text || !currentVideo.value) return
  await addComment(currentVideo.value.id, text)
  commentText.value = ''
  const vo = await listComments(currentVideo.value.id, 1, 50)
  comments.value = vo.data || []
  currentVideo.value.commentCount = Number(currentVideo.value.commentCount || 0) + 1
}

function onPublished(videoId) {
  publishVisible.value = false
  router.push(`/video/${videoId}`)
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  userStore.refreshBalance()
  setupObserver()
  await loadFeed(true)
})

onUnmounted(() => {
  observer && observer.disconnect()
  videoEls.forEach(el => { try { el.pause() } catch { } })
})
</script>

<style scoped>
.feed-page { height: 100vh; overflow: hidden; background: #000; color: #fff; }
.feed-nav {
  position: fixed; top: 0; left: 0; right: 0; z-index: 20;
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 20px; background: linear-gradient(to bottom, rgba(0,0,0,0.7), transparent);
}
.nav-left { display: flex; align-items: center; gap: 16px; }
.logo { font-weight: bold; cursor: pointer; }
.nav-tab { color: #aaa; cursor: pointer; font-size: 14px; }
.nav-tab.active { color: #fff; font-weight: bold; }
.nav-right { display: flex; align-items: center; gap: 12px; }
.avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; cursor: pointer; }
.balance-chip { font-size: 13px; cursor: pointer; }

.feed-scroll {
  height: 100vh; overflow-y: scroll; scroll-snap-type: y mandatory;
  scrollbar-width: none;
}
.feed-scroll::-webkit-scrollbar { display: none; }

.feed-item {
  position: relative; height: 100vh; scroll-snap-align: start;
  display: flex; align-items: center; justify-content: center; background: #000;
}
.feed-video { max-height: 100%; max-width: 100%; width: min(100vw, calc(100vh * 9 / 16)); object-fit: contain; }

.pause-mask {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,0.25); pointer-events: none;
}
.play-icon { font-size: 56px; opacity: 0.8; }

.heart-layer { position: absolute; inset: 0; pointer-events: none; }
.heart { position: absolute; font-size: 42px; animation: heart-pop 0.9s ease-out forwards; }
@keyframes heart-pop {
  0% { transform: scale(0.4) translateY(0); opacity: 1; }
  100% { transform: scale(1.6) translateY(-90px); opacity: 0; }
}

.action-col {
  position: absolute; right: 16px; bottom: 110px; z-index: 10;
  display: flex; flex-direction: column; gap: 20px; align-items: center;
}
.action-item { display: flex; flex-direction: column; align-items: center; gap: 3px; cursor: pointer; position: relative; }
.action-icon { font-size: 30px; filter: drop-shadow(0 1px 3px rgba(0,0,0,0.6)); }
.action-num { font-size: 12px; color: #eee; }
.action-avatar { width: 44px; height: 44px; border-radius: 50%; object-fit: cover; border: 2px solid #fff; }

.info-area { position: absolute; left: 16px; bottom: 90px; max-width: 70%; z-index: 10; }
.info-title { font-size: 15px; text-shadow: 0 1px 3px rgba(0,0,0,0.7); }
.info-author { font-size: 13px; color: #ddd; margin-top: 6px; }

.mute-toggle {
  position: absolute; right: 16px; top: 70px; z-index: 10;
  font-size: 22px; cursor: pointer; filter: drop-shadow(0 1px 2px #000);
}

.feed-empty {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  scroll-snap-align: start; color: #666; font-size: 14px;
}

/* 评论底部抽屉 */
.comment-panel { display: flex; flex-direction: column; height: 100%; }
.comment-head { font-weight: bold; padding: 12px 16px; border-bottom: 1px solid #2a3040; }
.comment-list { flex: 1; overflow-y: auto; padding: 8px 16px; }
.comment-row { display: flex; gap: 10px; padding: 8px 0; }
.comment-avatar { width: 34px; height: 34px; border-radius: 50%; object-fit: cover; }
.comment-name { font-size: 12px; color: #888; }
.comment-content { font-size: 14px; margin-top: 2px; }
.comment-empty { text-align: center; color: #666; padding: 32px 0; }
.comment-input-row { display: flex; gap: 10px; padding: 12px 16px; border-top: 1px solid #2a3040; }
.comment-input {
  flex: 1; padding: 8px 12px; border-radius: 6px; border: 1px solid #2a3040;
  background: #11151c; color: #fff; outline: none;
}
</style>
