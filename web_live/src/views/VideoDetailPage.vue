<template>
  <div class="video-detail-page">
    <header class="nav-bar">
      <span class="back" @click="$router.back()">← 返回</span>
      <span class="title">视频播放</span>
      <span class="balance-chip" @click="$router.push('/wallet')" title="去充值">
        <span class="coin-icon">🪙</span>{{ formatBalance(userStore.balance) }}
      </span>
    </header>

    <div class="detail-body" v-if="video">
      <div class="player-col">
        <video
          ref="playerRef"
          class="player"
          :src="video.videoUrl"
          :poster="video.coverUrl"
          controls
          autoplay
          playsinline
        ></video>
        <h1 class="video-title">{{ video.title }}</h1>
        <div class="meta-row">
          <img :src="video.avatar || defaultAvatar" class="author-avatar" />
          <span class="author">{{ video.nickName || '未知用户' }}</span>
          <span class="publish-time">{{ video.createTime }} 发布</span>
        </div>
        <div class="action-row">
          <button :class="['action-btn', { active: video.liked }]" @click="toggleLike">
            👍 {{ formatCount(video.likeCount) }}
          </button>
          <button :class="['action-btn', { active: video.favorited }]" @click="toggleFavorite">
            ⭐ {{ formatCount(video.favoriteCount) }}
          </button>
          <button class="action-btn" @click="doShare">🔗 {{ formatCount(video.shareCount) }}</button>
          <button class="action-btn" @click="focusComment">💬 {{ formatCount(video.commentCount) }}</button>
        </div>
        <p class="desc" v-if="video.description">{{ video.description }}</p>

        <!-- 评论区 -->
        <div class="comment-section">
          <div class="comment-title">全部评论（{{ formatCount(video.commentCount) }}）</div>
          <div class="comment-input-row">
            <input
              ref="commentInputRef"
              v-model="commentText"
              class="comment-input"
              placeholder="说点什么..."
              maxlength="200"
              @keyup.enter="submitComment"
            />
            <el-button type="primary" size="small" :disabled="!commentText.trim()" @click="submitComment">发送</el-button>
          </div>
          <div class="comment-list">
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <img :src="c.avatar || defaultAvatar" class="comment-avatar" />
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-name">{{ c.nickName || '用户' }}</span>
                  <span class="comment-time">{{ c.createTime }}</span>
                  <span
                    v-if="c.userId === userStore.userInfo.userId"
                    class="comment-del"
                    @click="removeComment(c.id)"
                  >删除</span>
                </div>
                <div class="comment-content">{{ c.content }}</div>
              </div>
            </div>
            <div v-if="comments.length === 0" class="comment-empty">还没有评论，抢个沙发~</div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="loading">{{ loadError || '加载中...' }}</div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { videoDetail, likeVideo, favoriteVideo, shareVideo, listComments, addComment, deleteComment, recordHistory } from '@/api/video'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const video = ref(null)
const comments = ref([])
const commentText = ref('')
const commentInputRef = ref(null)
const loadError = ref('')
const defaultAvatar = 'https://via.placeholder.com/40/667eea/fff?text=U'

const formatBalance = (n) => (Number(n) || 0).toLocaleString()
const formatCount = (n) => {
  n = Number(n) || 0
  return n >= 10000 ? (n / 10000).toFixed(1) + 'w' : String(n)
}

async function fetchDetail() {
  try {
    const vo = await videoDetail(route.params.id)
    video.value = vo.data?.item || null
    if (!video.value) loadError.value = '视频不存在或已下架'
  } catch (e) {
    loadError.value = e?.msg || '加载失败'
  }
}

async function fetchComments() {
  try {
    const vo = await listComments(route.params.id)
    comments.value = vo.data || []
  } catch { /* 忽略 */ }
}

async function toggleLike() {
  if (!userStore.userInfo.loginStatus) { router.push('/login'); return }
  const vo = await likeVideo(video.value.id, !video.value.liked)
  video.value.liked = !!vo.data
  video.value.likeCount = Math.max(0, Number(video.value.likeCount) + (vo.data ? 1 : -1))
}

async function toggleFavorite() {
  if (!userStore.userInfo.loginStatus) { router.push('/login'); return }
  const vo = await favoriteVideo(video.value.id, !video.value.favorited)
  video.value.favorited = !!vo.data
  video.value.favoriteCount = Math.max(0, Number(video.value.favoriteCount) + (vo.data ? 1 : -1))
}

async function doShare() {
  await shareVideo(video.value.id)
  video.value.shareCount = Number(video.value.shareCount) + 1
  try {
    await navigator.clipboard.writeText(location.href)
    ElMessage.success('链接已复制，分享计数 +1')
  } catch {
    ElMessage.success('分享计数 +1')
  }
}

async function submitComment() {
  if (!userStore.userInfo.loginStatus) { router.push('/login'); return }
  const content = commentText.value.trim()
  if (!content) return
  try {
    const vo = await addComment(video.value.id, content)
    comments.value.unshift(vo.data)
    video.value.commentCount = Number(video.value.commentCount) + 1
    commentText.value = ''
  } catch { /* 拦截器提示 */ }
}

async function removeComment(commentId) {
  try {
    await deleteComment(commentId)
    comments.value = comments.value.filter(c => c.id !== commentId)
    video.value.commentCount = Math.max(0, Number(video.value.commentCount) - 1)
  } catch { /* 拦截器提示 */ }
}

function focusComment() {
  commentInputRef.value?.focus()
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  await fetchDetail()
  await fetchComments()
  // 播放≥3秒记录观看历史（静默上报）；等 v-if 的播放器渲染出来再挂监听
  await nextTick()
  const player = playerRef.value
  if (player) {
    player.addEventListener('timeupdate', function onTime(e) {
      if (e.target.currentTime >= 3) {
        player.removeEventListener('timeupdate', onTime)
        if (userStore.userInfo.userId) recordHistory(route.params.id).catch(() => {})
      }
    })
  }
})
</script>

<style scoped>
.video-detail-page { min-height: 100vh; background: #0a0a0a; color: #fff; }
.nav-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; background: #161625; border-bottom: 1px solid #222;
}
.back { color: #667eea; cursor: pointer; }
.title { font-size: 16px; font-weight: bold; }
.balance-chip {
  display: inline-flex; align-items: center; gap: 4px;
  background: linear-gradient(135deg, #3a2c00, #4a3a00);
  border: 1px solid #7a5c00; color: #ffd700;
  font-size: 13px; font-weight: bold;
  padding: 4px 12px; border-radius: 16px; cursor: pointer;
}
.coin-icon { font-size: 13px; }

.detail-body { max-width: 860px; margin: 0 auto; padding: 20px; }
.player { width: 100%; border-radius: 12px; background: #000; outline: none; }
.video-title { font-size: 20px; margin: 16px 0 10px; }
.meta-row { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.author-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
.author { color: #667eea; font-size: 14px; }
.publish-time { color: #555; font-size: 12px; }

.action-row { display: flex; gap: 14px; margin-bottom: 16px; }
.action-btn {
  background: #1e1e2e; border: 1px solid #2c2c44; color: #ddd;
  padding: 8px 18px; border-radius: 20px; cursor: pointer; font-size: 14px;
  transition: all 0.2s;
}
.action-btn:hover { border-color: #667eea; }
.action-btn.active { color: #ffd700; border-color: #ffd700; }
.desc { color: #999; font-size: 14px; line-height: 1.7; margin-bottom: 20px; }

.comment-section { border-top: 1px solid #222; padding-top: 18px; }
.comment-title { font-size: 15px; margin-bottom: 14px; }
.comment-input-row { display: flex; gap: 10px; margin-bottom: 20px; }
.comment-input {
  flex: 1; padding: 10px 14px; border: 1px solid #333; border-radius: 20px;
  background: #1a1a2e; color: #fff; font-size: 14px; outline: none;
}
.comment-input:focus { border-color: #667eea; }
.comment-input::placeholder { color: #555; }
.comment-list { display: flex; flex-direction: column; gap: 16px; }
.comment-item { display: flex; gap: 10px; align-items: flex-start; }
.comment-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.comment-body { flex: 1; }
.comment-meta { display: flex; gap: 10px; align-items: center; margin-bottom: 4px; }
.comment-name { font-size: 13px; color: #667eea; }
.comment-time { font-size: 12px; color: #555; }
.comment-del { font-size: 12px; color: #f56c6c; cursor: pointer; }
.comment-content { font-size: 14px; color: #ccc; line-height: 1.5; }
.comment-empty { text-align: center; color: #444; padding: 24px 0; }
.loading { text-align: center; color: #666; padding: 100px 0; }
</style>
