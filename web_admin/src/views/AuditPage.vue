<template>
  <div>
    <div class="page-head"><h2 class="page-title">视频审核队列（审核中 {{ list.length }}）</h2><el-button @click="load">刷新</el-button></div>
    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="140">
        <template #default="{ row }">
          <el-image :src="row.coverUrl" fit="cover" style="width: 120px; height: 68px; border-radius: 4px"
            :preview-src-list="[row.coverUrl]" preview-teleported hide-on-click-modal>
            <template #error><div class="cover-fallback">无封面</div></template>
          </el-image>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="nickName" label="作者" width="140" />
      <el-table-column label="转码" width="90">
        <template #default="{ row }">
          <span :class="row.transcodeStatus === 1 ? 'st-on' : 'st-off'">
            {{ row.transcodeStatus === 1 ? '已完成' : row.transcodeStatus === 0 ? '处理中' : '未转码' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="review(row, true)">通过</el-button>
          <el-button size="small" type="danger" @click="review(row, false)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="list.length === 0" description="队列为空，全部处理完毕" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { post } from '@/api'

const list = ref([])

async function load() {
  const vo = await post('/video/reviewList', { page: 1, pageSize: 50 })
  list.value = vo.data.list || []
}

async function review(row, pass) {
  await post('/video/review', { id: row.id, pass })
  ElMessage.success(pass ? '已通过上线' : '已驳回下架')
  load()
}

onMounted(load)
</script>

<style scoped>
.cover-fallback { width: 120px; height: 68px; display: flex; align-items: center; justify-content: center; color: #999; font-size: 12px; background: #f5f5f5; border-radius: 4px; }
.st-on { color: #67c23a; }
.st-off { color: #e6a23c; }
</style>
