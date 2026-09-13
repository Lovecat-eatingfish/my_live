<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">敏感词管理</h2>
      <div class="head-actions">
        <el-input v-model="newWord.word" placeholder="新敏感词" style="width: 200px" />
        <el-select v-model="newWord.level" style="width: 110px">
          <el-option :value="1" label="1 拦截" />
          <el-option :value="2" label="2 替换*" />
          <el-option :value="3" label="3 仅记录" />
        </el-select>
        <el-select v-model="newWord.scene" style="width: 130px">
          <el-option :value="0" label="全部场景" />
          <el-option :value="1" label="弹幕" />
          <el-option :value="2" label="昵称" />
          <el-option :value="3" label="视频标题" />
          <el-option :value="4" label="评论" />
          <el-option :value="5" label="房间名" />
        </el-select>
        <el-button type="primary" :disabled="!newWord.word.trim()" @click="add">添加</el-button>
      </div>
    </div>

    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="word" label="敏感词" min-width="160" />
      <el-table-column label="级别" width="120">
        <template #default="{ row }">{{ ['','拦截','替换*','仅记录'][row.level] || row.level }}</template>
      </el-table-column>
      <el-table-column label="场景" width="140">
        <template #default="{ row }">{{ ['全部','弹幕','昵称','视频标题','评论','房间名'][row.scene] || row.scene }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="danger" text @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { post } from '@/api'

const list = ref([])
const newWord = reactive({ word: '', level: 1, scene: 0 })

async function load() {
  const vo = await post('/riskWord/list')
  list.value = vo.data || []
}

async function add() {
  if (!newWord.word.trim()) return
  await post('/riskWord/add', { ...newWord })
  ElMessage.success('已添加（词库热更新即时生效）')
  newWord.word = ''
  load()
}

async function del(row) {
  await post('/riskWord/delete', { id: row.id })
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.head-actions { display: flex; gap: 10px; align-items: center; }
</style>
