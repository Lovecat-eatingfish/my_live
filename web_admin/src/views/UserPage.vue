<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">用户管理</h2>
      <div class="head-actions">
        <el-input v-model="keyword" placeholder="昵称 / 用户ID" style="width: 220px" clearable @keyup.enter="load" />
        <el-button type="primary" @click="load">搜索</el-button>
      </div>
    </div>

    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="userId" label="用户ID" width="140" />
      <el-table-column label="头像" width="70">
        <template #default="{ row }">
          <img :src="row.avatar || defaultAvatar" class="avatar" />
        </template>
      </el-table-column>
      <el-table-column prop="nickName" label="昵称" min-width="160" />
      <el-table-column prop="sex" label="性别" width="80">
        <template #default="{ row }">{{ row.sex === 1 ? '男' : row.sex === 0 ? '女' : '未知' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { post } from '@/api'

const list = ref([])
const keyword = ref('')
const defaultAvatar = 'https://via.placeholder.com/32/0F6B5C/fff?text=U'

async function load() {
  const vo = await post('/user/list', { keyword: keyword.value || undefined, page: 1, pageSize: 50 })
  list.value = vo.data || []
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { margin: 0; font-size: 20px; }
.head-actions { display: flex; gap: 10px; }
.avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
</style>
