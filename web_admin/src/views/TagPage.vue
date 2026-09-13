<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">标签管理</h2>
      <div class="head-actions">
        <el-input v-model="newName" placeholder="新标签名" style="width: 200px" maxlength="10" />
        <el-button type="primary" :disabled="!newName.trim()" @click="handleAdd">新增标签</el-button>
      </div>
    </div>

    <el-table :data="list" stripe style="width: 100%; max-width: 640px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="tagName" label="标签名" min-width="140" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openRename(row)">重命名</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="renameVisible" title="重命名标签" width="380px">
      <el-input v-model="renameName" maxlength="10" placeholder="新名称" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="doRename">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { post } from '@/api'

const list = ref([])
const newName = ref('')
const renameVisible = ref(false)
const renameId = ref(null)
const renameName = ref('')

async function load() {
  const vo = await post('/tag/list')
  list.value = vo.data || []
}

async function handleAdd() {
  await post('/tag/add', { name: newName.value.trim() })
  ElMessage.success('已新增')
  newName.value = ''
  await load()
}

function openRename(row) {
  renameId.value = row.id
  renameName.value = row.tagName
  renameVisible.value = true
}

async function doRename() {
  await post('/tag/rename', { id: renameId.value, name: renameName.value.trim() })
  ElMessage.success('已保存')
  renameVisible.value = false
  await load()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除标签「${row.tagName}」？已发布的视频将不再显示该分类。`, '删除标签', { type: 'warning' })
  await post('/tag/delete', { id: row.id })
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { margin: 0; font-size: 20px; }
.head-actions { display: flex; gap: 10px; }
</style>
