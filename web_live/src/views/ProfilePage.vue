<template>
  <div class="profile-page">
    <header class="nav-bar">
      <div class="nav-left">
        <span class="back" @click="$router.back()">← 返回</span>
      </div>
      <div class="nav-right">
        <span class="nickname">{{ userStore.userInfo.nickName }}</span>
      </div>
    </header>

    <!-- 资料卡 -->
    <div class="profile-card" v-if="profile.userId">
      <img :src="profile.avatar || defaultAvatar" class="avatar" />
      <div class="info">
        <div class="name-row">
          <span class="nick">{{ profile.nickName || ('用户' + profile.userId) }}</span>
          <span class="lv-badge" :class="levelClass(profile.level)">L{{ profile.level }}</span>
        </div>
        <div class="uid">UID: {{ profile.userId }} · 注册于 {{ formatDate(profile.createTime) }}</div>
        <!-- 经验进度（封顶不显示） -->
        <div class="exp-row" v-if="profile.nextLevelExp > 0">
          <div class="exp-bar">
            <div class="exp-fill" :style="{ width: expPercent + '%' }"></div>
          </div>
          <span class="exp-text">EXP {{ profile.exp }} / {{ profile.nextLevelExp }}</span>
        </div>
        <div class="exp-row" v-else>
          <span class="exp-text">已达最高等级 🏆</span>
        </div>
        <div class="stats-row">
          <span>关注 <b>{{ profile.followCnt }}</b></span>
          <span>粉丝 <b>{{ profile.fansCnt }}</b></span>
          <span>获赞 <b>{{ profile.likeReceivedCnt }}</b></span>
        </div>
      </div>
      <div class="actions" v-if="!profile.isSelf">
        <el-button :type="profile.isFollow ? 'info' : 'primary'" size="small" @click="toggleFollow">
          {{ profile.isMutual ? '🤝 互相关注' : (profile.isFollow ? '已关注' : '+ 关注') }}
        </el-button>
        <!-- 私信入口：跳转消息页并直达该用户的会话（无历史会话也可发起） -->
        <el-button size="small" @click="router.push(`/messages?peer=${profile.userId}`)">✉️ 发私信</el-button>
      </div>
    </div>

    <!-- 内容 Tab -->
    <div class="content-tabs">
      <span :class="['tab', { active: activeTab === 'videos' }]" @click="switchTab('videos')">
        {{ profile.isSelf ? '我的视频' : 'TA 的视频' }}
      </span>
      <span :class="['tab', { active: activeTab === 'follow' }]" @click="switchTab('follow')">关注列表</span>
      <span :class="['tab', { active: activeTab === 'fans' }]" @click="switchTab('fans')">粉丝列表</span>
      <span :class="['tab', { active: activeTab === 'replays' }]" @click="switchTab('replays')">
        直播回放{{ replays.length ? `(${replays.length})` : '' }}
      </span>
    </div>

    <!-- 视频列表 -->
    <div class="video-grid" v-if="activeTab === 'videos'">
      <div class="video-card" v-for="v in videos" :key="v.id" @click="$router.push(`/video/${v.id}`)">
        <div class="cover-wrap">
          <img :src="v.coverUrl || defaultCover" class="cover" />
          <span class="play-count">▶ {{ v.playCount }}</span>
        </div>
        <div class="video-title">{{ v.title }}</div>
      </div>
      <div v-if="videos.length === 0" class="empty">还没有发布过视频</div>
    </div>

    <!-- 直播回放列表 -->
    <div class="replay-list" v-if="activeTab === 'replays'">
      <div class="replay-card" v-for="rec in replays" :key="rec.id">
        <ReplayPlayer :src="rec.recordUrl" />
        <div class="replay-meta">{{ formatDate(rec.startTime) }} · 时长 {{ formatDuration(rec.duration) }}</div>
      </div>
      <div v-if="replays.length === 0" class="empty">还没有直播回放</div>
    </div>

    <!-- 关注/粉丝列表 -->
    <div class="user-list" v-if="activeTab === 'follow' || activeTab === 'fans'">
      <div class="user-row" v-for="u in relationUsers" :key="u.userId" @click="$router.push(`/profile/${u.userId}`)">
        <img :src="u.avatar || defaultAvatar" class="row-avatar" />
        <span class="row-nick">{{ u.nickName || ('用户' + u.userId) }}</span>
        <span class="row-enter">›</span>
      </div>
      <div v-if="relationUsers.length === 0" class="empty">
        {{ activeTab === 'follow' ? '还没有关注任何人' : '还没有粉丝' }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getUserProfile, followUser, unfollowUser, followList, fansList } from '@/api/user'
import { userVideos } from '@/api/video'
import { getRecordsByAnchor } from '@/api/stream'
import ReplayPlayer from '@/components/ReplayPlayer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const defaultAvatar = 'data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2280%22%20height%3D%2280%22%3E%3Crect%20width%3D%22100%25%22%20height%3D%22100%25%22%20fill%3D%22%23667eea%22%2F%3E%3Ccircle%20cx%3D%2240%22%20cy%3D%2230%22%20r%3D%2214%22%20fill%3D%22%23ffffffaa%22%2F%3E%3Cpath%20d%3D%22M14%2074%20Q40%2048%2066%2074%20Z%22%20fill%3D%22%23ffffffaa%22%2F%3E%3C%2Fsvg%3E'
const defaultCover = 'data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22320%22%20height%3D%22180%22%3E%3Cdefs%3E%3ClinearGradient%20id%3D%22g%22%20x1%3D%220%22%20y1%3D%220%22%20x2%3D%221%22%20y2%3D%221%22%3E%3Cstop%20offset%3D%220%22%20stop-color%3D%22%231a1a2e%22%2F%3E%3Cstop%20offset%3D%221%22%20stop-color%3D%22%234a3a8e%22%2F%3E%3C%2FlinearGradient%3E%3C%2Fdefs%3E%3Crect%20width%3D%22100%25%22%20height%3D%22100%25%22%20fill%3D%22url(%23g)%22%2F%3E%3Ctext%20x%3D%2250%25%22%20y%3D%2252%25%22%20dominant-baseline%3D%22middle%22%20text-anchor%3D%22middle%22%20font-family%3D%22sans-serif%22%20font-size%3D%2230%22%20fill%3D%22%23667eea%22%3ELIVE%3C%2Ftext%3E%3C%2Fsvg%3E'

const profile = ref({})
const videos = ref([])
const relationUsers = ref([])
const activeTab = ref('videos')

const profileUserId = computed(() => route.params.userId || userStore.userInfo.userId)

const expPercent = computed(() => {
  const p = profile.value
  if (!p.nextLevelExp || p.nextLevelExp <= 0 || !p.level) return 0
  const curFloor = [0, 100, 300, 600, 1000, 2000, 3500, 6000, 10000, 20000, 35000, 60000][p.level - 1] || 0
  const span = p.nextLevelExp - curFloor
  if (span <= 0) return 0
  return Math.min(100, Math.round(((p.exp - curFloor) / span) * 100))
})

function levelClass(level) {
  if (level >= 9) return 'lv-gold'
  if (level >= 5) return 'lv-blue'
  return 'lv-green'
}

const replays = ref([])

function formatDuration(sec) {
  const s = Number(sec) || 0
  const m = Math.floor(s / 60)
  return m > 0 ? `${m}分${s % 60}秒` : `${s}秒`
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString() : '-'
}

async function loadProfile() {
  const vo = await getUserProfile(profileUserId.value)
  profile.value = vo.data || {}
}

async function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'videos') {
    const vo = await userVideos(profileUserId.value)
    videos.value = vo.data || []
  } else if (tab === 'replays') {
    const vo = await getRecordsByAnchor(profileUserId.value)
    replays.value = vo.data || []
    return
  } else {
    const vo = tab === 'follow'
      ? await followList(1, 50)
      : await fansList(1, 50)
    relationUsers.value = (vo.data && vo.data.list) || []
  }
}

async function toggleFollow() {
  if (profile.value.isFollow) {
    await unfollowUser(profile.value.userId)
    profile.value.isFollow = false
    profile.value.fansCnt = Math.max(0, Number(profile.value.fansCnt) - 1)
    ElMessage.success('已取消关注')
  } else {
    await followUser(profile.value.userId)
    profile.value.isFollow = true
    profile.value.fansCnt = Number(profile.value.fansCnt) + 1
    ElMessage.success('关注成功')
  }
}

watch(profileUserId, () => {
  loadProfile()
  switchTab(activeTab.value)
}, { immediate: true })
</script>

<style scoped>
.profile-page { min-height: 100vh; background: #11151c; color: #e8e8e8; }
.nav-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 24px; background: #1a1f2b; position: sticky; top: 0; z-index: 10;
}
.back { cursor: pointer; color: #aaa; }
.back:hover { color: #fff; }
.nickname { font-size: 14px; }

.profile-card {
  display: flex; gap: 20px; align-items: center;
  max-width: 860px; margin: 24px auto; padding: 24px;
  background: #1a1f2b; border-radius: 12px;
}
.avatar { width: 84px; height: 84px; border-radius: 50%; object-fit: cover; }
.info { flex: 1; }
.name-row { display: flex; align-items: center; gap: 8px; }
.nick { font-size: 20px; font-weight: bold; }
.uid { font-size: 12px; color: #777; margin-top: 4px; }
.exp-row { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.exp-bar { width: 240px; height: 8px; background: #2a3040; border-radius: 4px; overflow: hidden; }
.exp-fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); border-radius: 4px; }
.exp-text { font-size: 12px; color: #999; }
.stats-row { display: flex; gap: 24px; margin-top: 12px; font-size: 13px; color: #aaa; }
.stats-row b { color: #fff; }
.lv-badge {
  display: inline-block; padding: 0 6px; border-radius: 4px;
  font-size: 12px; line-height: 18px; color: #fff;
}
.lv-green { background: #67c23a; }
.lv-blue { background: #409eff; }
.lv-gold { background: #e6a23c; }

.content-tabs {
  display: flex; gap: 24px; max-width: 860px; margin: 0 auto; padding: 0 24px 12px;
  border-bottom: 1px solid #2a3040;
}
.tab { cursor: pointer; color: #999; font-size: 15px; padding-bottom: 8px; }
.tab.active { color: #fff; font-weight: bold; border-bottom: 2px solid #667eea; }

.video-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
  max-width: 860px; margin: 20px auto; padding: 0 24px;
}
.video-card { cursor: pointer; }
.cover-wrap { position: relative; border-radius: 8px; overflow: hidden; background: #000; }
.cover { width: 100%; height: 135px; object-fit: cover; display: block; }
.play-count {
  position: absolute; left: 8px; bottom: 6px; font-size: 12px; color: #fff;
  text-shadow: 0 1px 2px #000;
}
.video-title { font-size: 13px; color: #ccc; margin-top: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.user-list { max-width: 860px; margin: 16px auto; padding: 0 24px; }
.user-row {
  display: flex; align-items: center; gap: 12px; padding: 10px 12px;
  border-radius: 8px; cursor: pointer;
}
.user-row:hover { background: #1a1f2b; }
.row-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.row-nick { flex: 1; font-size: 14px; }
.row-enter { color: #555; }

.empty { text-align: center; color: #555; padding: 48px 0; font-size: 14px; }
.replay-list { display: flex; flex-direction: column; gap: 16px; max-width: 640px; }
.replay-card { background: var(--sq-card, #fff); border-radius: 10px; padding: 10px; }
.replay-meta { font-size: 12px; color: #888; margin-top: 6px; }
</style>
