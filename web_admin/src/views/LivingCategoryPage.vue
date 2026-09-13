<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">直播分区管理</h2>
      <div class="head-actions">
        <el-input v-model="newCat.name" placeholder="分区名" style="width: 140px" maxlength="10" />
        <el-input v-model="newCat.icon" placeholder="图标(emoji/URL)" style="width: 160px" maxlength="50" />
        <el-input-number v-model="newCat.sort" :min="0" :max="999" controls-position="right" style="width: 110px" />
        <el-button type="primary" :disabled="!newCat.name.trim()" @click="handleAdd">新增分区</el-button>
      </div>
    </div>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px; max-width: 720px"
      title="分区 id 即开播接口的 type 值；停用分区后该分区不再出现在首页 tab，存量房间不受影响" />
    <el-table :data="list" stripe style="width: 100%; max-width: 760px">
      <el-table-column prop="id" label="ID(type)" width="90" />
      <el-table-column prop="icon" label="图标" width="70" />
      <el-table-column prop="name" label="分区名" min-width="120" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 1" size="small" text type="danger" @click="toggle(row, 0)">停用</el-button>
          <el-button v-else size="small" text type="success" @click="toggle(row, 1)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" title="编辑分区" width="420px">
      <el-form label-width="70px">
        <el-form-item label="分区名"><el-input v-model="editRow.name" maxlength="10" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="editRow.icon" maxlength="50" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editRow.sort" :min="0" :max="999" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="doEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api'

const list = ref([])
const newCat = reactive({ name: '', icon: '', sort: 99 })
const editVisible = ref(false)
const editRow = reactive({ id: 0, name: '', icon: '', sort: 0 })

async function load() {
  const vo = await request.post('/living/category/list')
  list.value = vo.data || []
}
async function handleAdd() {
  const vo = await request.post('/living/category/add', null, { params: { name: newCat.name.trim(), icon: newCat.icon, sort: newCat.sort } })
  if (vo.code !== 200) return
  ElMessage.success('已新增')
  newCat.name = ''; newCat.icon = ''; newCat.sort = 99
  load()
}
function openEdit(row) {
  editRow.id = row.id; editRow.name = row.name; editRow.icon = row.icon; editRow.sort = row.sort
  editVisible.value = true
}
async function doEdit() {
  const vo = await request.post('/living/category/update', null, {
    params: { id: editRow.id, name: editRow.name.trim(), icon: editRow.icon, sort: editRow.sort }
  })
  if (vo.code !== 200) return
  ElMessage.success('已保存')
  editVisible.value = false
  load()
}
async function toggle(row, status) {
  const vo = await request.post('/living/category/update', null, { params: { id: row.id, status } })
  if (vo.code !== 200) return
  ElMessage.success(status === 1 ? '已启用' : '已停用')
  load()
}
onMounted(load)
</script>
