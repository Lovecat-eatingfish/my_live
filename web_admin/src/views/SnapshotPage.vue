<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">直播巡查（每 30s 自动截帧）</h2>
      <el-radio-group v-model="status" @change="load">
        <el-radio-button :value="0">待审</el-radio-button>
        <el-radio-button :value="2">已处置</el-radio-button>
      </el-radio-group>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="snap-grid">
      <div class="snap-card" v-for="s in list" :key="s.id">
        <el-image :src="s.img_url" fit="cover" class="snap-img"
          :preview-src-list="[s.img_url]" preview-teleported hide-on-click-modal />
        <div class="snap-meta">
          <div>房间 {{ s.room_id }} · 主播 {{ s.anchor_id }}</div>
          <div class="snap-time">{{ s.create_time }}</div>
        </div>
        <div class="snap-actions" v-if="status === 0">
          <el-button size="small" @click="handle(s, 'pass')">正常</el-button>
          <el-button size="small" type="warning" @click="handle(s, 'warn')">警告</el-button>
          <el-button size="small" type="danger" @click="handle(s, 'close')">强关</el-button>
          <el-button size="small" type="danger" @click="handle(s, 'ban')">封号</el-button>
        </div>
        <div class="snap-actions" v-else>
          <el-tag size="small" type="info">已处置：{{ s.handle_action }}</el-tag>
        </div>
      </div>
    </div>
    <el-empty v-if="list.length === 0" description="暂无截帧记录（有推流中的直播间后 30s 内出现）" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { post } from '@/api'

const list = ref([])
const status = ref(0)

async function load() {
  const vo = await post('/snapshot/list', { status: status.value, page: 1, pageSize: 50 })
  list.value = vo.data.list || []
}

async function handle(s, action) {
  const label = { warn: '已向主播发送警告', close: '已强制下播', ban: '已封禁主播 24h', pass: '已标记正常' }[action]
  await post('/snapshot/handle', { id: s.id, action })
  ElMessage.success(label)
  load()
}

onMounted(load)
</script>

<style scoped>
.snap-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.snap-card { background: #fff; border-radius: 10px; padding: 10px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
.snap-img { width: 100%; height: 130px; border-radius: 6px; }
.snap-meta { font-size: 12px; color: #666; margin: 8px 0; }
.snap-time { color: #aaa; }
.snap-actions { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
