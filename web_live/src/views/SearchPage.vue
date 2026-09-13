<template>
  <div class="search-page">
    <header class="nav-bar">
      <div class="nav-left">
        <span class="back" @click="$router.back()">← 返回</span>
        <input v-model="keyword" class="search-input" placeholder="搜索直播间 / 视频 / 用户"
          @keyup.enter="doSearch" />
        <el-button size="small" type="primary" @click="doSearch">搜索</el-button>
      </div>
    </header>

    <div class="result-sections" v-if="searched">
      <!-- 直播 -->
      <div class="section">
        <div class="section-title">直播间 <span class="cnt">{{ rooms.length }}</span></div>
        <div class="room-grid">
          <div class="room-card" v-for="r in rooms" :key="r.id" @click="$router.push(`/room/${r.id}`)">
            <img :src="r.covertImg || defaultCover" class="cover" />
            <div class="room-name">{{ r.roomName }}</div>
            <div class="room-meta">{{ r.watchNum || 0 }} 人观看</div>
          </div>
        </div>
        <div v-if="rooms.length === 0" class="empty">没有相关直播间</div>
      </div>

      <!-- 视频 -->
      <div class="section">
        <div class="section-title">视频 <span class="cnt">{{ videos.length }}</span></div>
        <div class="video-grid">
          <div class="video-card" v-for="v in videos" :key="v.id" @click="$router.push(`/video/${v.id}`)">
            <div class="cover-wrap">
              <img :src="v.coverUrl || defaultCover" class="cover" />
              <span class="play-count">▶ {{ v.playCount }}</span>
            </div>
            <div class="video-title">{{ v.title }}</div>
            <div class="video-author">{{ v.nickName || '未知用户' }}</div>
          </div>
        </div>
        <div v-if="videos.length === 0" class="empty">没有相关视频</div>
      </div>

      <!-- 用户 -->
      <div class="section">
        <div class="section-title">用户 <span class="cnt">{{ users.length }}</span></div>
        <div class="user-list">
          <div class="user-row" v-for="u in users" :key="u.userId" @click="$router.push(`/profile/${u.userId}`)">
            <img :src="u.avatar || defaultAvatar" class="row-avatar" />
            <span class="row-nick">{{ u.nickName || ('用户' + u.userId) }}</span>
            <span class="row-enter">›</span>
          </div>
        </div>
        <div v-if="users.length === 0" class="empty">没有相关用户</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { searchAll } from '@/api/search'

const route = useRoute()
const keyword = ref(route.query.keyword || '')
const rooms = ref([])
const videos = ref([])
const users = ref([])
const searched = ref(false)

const defaultCover = 'https://via.placeholder.com/240x135/222/666?text=Live'
const defaultAvatar = 'https://via.placeholder.com/80/667eea/fff?text=U'

async function doSearch() {
  if (!keyword.value.trim()) return
  const vo = await searchAll(keyword.value.trim())
  rooms.value = vo.data?.rooms || []
  videos.value = vo.data?.videos || []
  users.value = vo.data?.users || []
  searched.value = true
}

doSearch()
</script>

<style scoped>
.search-page { min-height: 100vh; background: #11151c; color: #e8e8e8; }
.nav-bar {
  display: flex; padding: 12px 24px; background: #1a1f2b;
  position: sticky; top: 0; z-index: 10;
}
.nav-left { display: flex; align-items: center; gap: 12px; }
.back { cursor: pointer; color: #aaa; }
.back:hover { color: #fff; }
.search-input {
  width: 320px; padding: 8px 12px; border-radius: 6px; border: 1px solid #2a3040;
  background: #11151c; color: #fff; outline: none;
}
.search-input:focus { border-color: #667eea; }

.result-sections { max-width: 960px; margin: 0 auto; padding: 16px 24px; }
.section { margin-bottom: 32px; }
.section-title { font-size: 16px; font-weight: bold; margin-bottom: 12px; }
.cnt { color: #667eea; font-size: 13px; margin-left: 6px; }

.room-grid, .video-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.room-card, .video-card { cursor: pointer; }
.cover { width: 100%; height: 120px; object-fit: cover; display: block; border-radius: 8px; background: #000; }
.cover-wrap { position: relative; border-radius: 8px; overflow: hidden; }
.play-count { position: absolute; left: 8px; bottom: 6px; font-size: 12px; color: #fff; text-shadow: 0 1px 2px #000; }
.room-name, .video-title {
  font-size: 13px; margin-top: 6px; color: #ccc;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.video-author { font-size: 12px; color: #666; margin-top: 2px; }
.room-meta { font-size: 12px; color: #666; margin-top: 2px; }

.user-list { display: flex; flex-direction: column; }
.user-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 8px; cursor: pointer; }
.user-row:hover { background: #1a1f2b; }
.row-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.row-nick { flex: 1; font-size: 14px; }
.row-enter { color: #555; }

.empty { text-align: center; color: #555; padding: 24px 0; font-size: 13px; }
</style>
