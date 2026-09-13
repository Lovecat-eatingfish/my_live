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
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" type="warning" @click="openBan(row, 1)">禁言</el-button>
          <el-button size="small" type="danger" @click="openBan(row, 2)">封号</el-button>
          <el-button size="small" @click="unban(row)">解封</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="banVisible" :title="banType === 1 ? '禁言用户' : '封禁账号'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="用户">{{ banUser?.nickName }}（{{ banUser?.userId }}）</el-form-item>
        <el-form-item label="时长">
          <el-select v-model="banMinutes" style="width: 100%">
            <el-option label="永久" :value="0" />
            <el-option label="10 分钟" :value="10" />
            <el-option label="1 小时" :value="60" />
            <el-option label="24 小时" :value="1440" />
            <el-option label="7 天" :value="10080" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="banReason" placeholder="违规原因（留档）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBan">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { post } from '@/api'

const list = ref([])
const keyword = ref('')
const defaultAvatar = 'https://via.placeholder.com/32/0F6B5C/fff?text=U'

const banVisible = ref(false)
const banUser = ref(null)
const banType = ref(1)
const banMinutes = ref(60)
const banReason = ref('')

async function load() {
  const vo = await post('/user/list', { keyword: keyword.value || undefined, page: 1, pageSize: 50 })
  list.value = vo.data || []
}

function openBan(row, type) {
  banUser.value = row
  banType.value = type
  banMinutes.value = 60
  banReason.value = ''
  banVisible.value = true
}

async function confirmBan() {
  await post('/user/ban', {
    userId: banUser.value.userId,
    type: banType.value,
    minutes: banMinutes.value,
    reason: banReason.value || undefined
  })
  ElMessage.success(banType.value === 1 ? '已禁言' : '已封号')
  banVisible.value = false
}

async function unban(row) {
  await post('/user/unban', { userId: row.userId, type: 2 })
  await post('/user/unban', { userId: row.userId, type: 1 })
  ElMessage.success('已解除封禁/禁言')
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { margin: 0; font-size: 20px; }
.head-actions { display: flex; gap: 10px; }
.avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
</style>
