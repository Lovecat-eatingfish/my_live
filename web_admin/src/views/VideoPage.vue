<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">内容管理</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="nickName" label="作者" width="140" />
      <el-table-column prop="tagName" label="分类" width="90" />
      <el-table-column label="播放" width="90">
        <template #default="{ row }"><span class="num">{{ row.playCount }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <!-- 状态枚举对齐 provider：1=已上架 2=审核中 0=已下架 -->
          <span :class="row.status === 1 ? 'st-on' : 'st-off'">{{ { 1: '已上架', 2: '审核中' }[row.status] || '已下架' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="转码" width="90">
        <template #default="{ row }">
          <span :class="row.transcodeStatus === 2 ? 'st-off' : 'st-on'">{{ { 0: '处理中', 1: '已完成', 2: '失败' }[row.transcodeStatus] || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button size="small" text :type="row.status === 1 ? 'danger' : 'primary'" @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button v-if="row.transcodeStatus !== 1" size="small" text type="warning" @click="retryTranscode(row)">
            重试转码
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { post } from '@/api'

const list = ref([])

async function load() {
  const vo = await post('/video/list', { page: 1, pageSize: 50 })
  list.value = vo.data.list || []
}

async function toggle(row) {
  const toOff = row.status === 1
  await ElMessageBox.confirm(`${toOff ? '下架' : '上架'}视频「${row.title}」？`, '确认操作', { type: 'warning' })
  await post('/video/setStatus', { id: row.id, status: toOff ? 0 : 1 })
  ElMessage.success(toOff ? '已下架' : '已上架')
  await load()
}

async function retryTranscode(row) {
  const vo = await post('/video/transcodeRetry', { id: row.id })
  if (vo.data) {
    ElMessage.success('已重新投递转码任务，稍后刷新查看结果')
  } else {
    ElMessage.warning('无法重试：视频不存在或转码已完成')
  }
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { margin: 0; font-size: 20px; }
.st-on { color: var(--brand); font-size: 13px; }
.st-off { color: var(--danger); font-size: 13px; }
</style>
