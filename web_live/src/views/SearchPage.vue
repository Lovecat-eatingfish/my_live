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

const defaultCover = 'data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22320%22%20height%3D%22180%22%3E%3Cdefs%3E%3ClinearGradient%20id%3D%22g%22%20x1%3D%220%22%20y1%3D%220%22%20x2%3D%221%22%20y2%3D%221%22%3E%3Cstop%20offset%3D%220%22%20stop-color%3D%22%231a1a2e%22%2F%3E%3Cstop%20offset%3D%221%22%20stop-color%3D%22%234a3a8e%22%2F%3E%3C%2FlinearGradient%3E%3C%2Fdefs%3E%3Crect%20width%3D%22100%25%22%20height%3D%22100%25%22%20fill%3D%22url(%23g)%22%2F%3E%3Ctext%20x%3D%2250%25%22%20y%3D%2252%25%22%20dominant-baseline%3D%22middle%22%20text-anchor%3D%22middle%22%20font-family%3D%22sans-serif%22%20font-size%3D%2230%22%20fill%3D%22%23667eea%22%3ELIVE%3C%2Ftext%3E%3C%2Fsvg%3E'
const defaultAvatar = 'data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2280%22%20height%3D%2280%22%3E%3Crect%20width%3D%22100%25%22%20height%3D%22100%25%22%20fill%3D%22%23667eea%22%2F%3E%3Ccircle%20cx%3D%2240%22%20cy%3D%2230%22%20r%3D%2214%22%20fill%3D%22%23ffffffaa%22%2F%3E%3Cpath%20d%3D%22M14%2074%20Q40%2048%2066%2074%20Z%22%20fill%3D%22%23ffffffaa%22%2F%3E%3C%2Fsvg%3E'

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
