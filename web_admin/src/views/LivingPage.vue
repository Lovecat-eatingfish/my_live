<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">直播管理</h2>
      <div class="head-actions">
        <el-radio-group v-model="type" @change="load">
          <el-radio-button :value="0">全部</el-radio-button>
          <el-radio-button :value="1">娱乐</el-radio-button>
          <el-radio-button :value="2">游戏</el-radio-button>
          <el-radio-button :value="3">赛事</el-radio-button>
          <el-radio-button :value="4">带货</el-radio-button>
        </el-radio-group>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="roomId" label="房间ID" width="100" />
      <el-table-column prop="roomName" label="房间名" min-width="200" show-overflow-tooltip />
      <el-table-column prop="anchorId" label="主播ID" width="140" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ typeName(row.type) }}</template>
      </el-table-column>
      <el-table-column label="观看" width="90">
        <template #default="{ row }"><span class="num">{{ row.watchNum || 0 }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button size="small" text type="danger" @click="forceClose(row)">强制关播</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!list.length" class="empty">当前没有进行中的直播</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { post } from '@/api'

const list = ref([])
const type = ref(0)

const typeName = (t) => ({ 1: '娱乐', 2: '游戏', 3: '赛事', 4: '带货' }[t] || t)

async function load() {
  const vo = await post('/living/list', { type: type.value })
  list.value = vo.data || []
}

async function forceClose(row) {
  await ElMessageBox.confirm(
    `强制关闭直播间「${row.roomName}」（房间ID ${row.roomId}）？观众将收到关播通知。`,
    '强制关播', { type: 'warning', confirmButtonText: '确认关播' }
  )
  await post('/living/forceClose', { roomId: row.roomId })
  ElMessage.success('已关闭')
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.page-title { margin: 0; font-size: 20px; }
.head-actions { display: flex; gap: 10px; align-items: center; }
.empty { text-align: center; color: var(--ink-2); padding: 80px 0; }
</style>
