<template>
  <div>
    <div class="page-head"><h2 class="page-title">礼物配置</h2><el-button @click="load">刷新</el-button></div>
    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="giftId" label="ID" width="70" />
      <el-table-column prop="giftName" label="名称" min-width="140" />
      <el-table-column label="价格（金币）" width="160">
        <template #default="{ row }">
          <el-input-number v-model="row.price" :min="1" size="small" @change="dirty.add(row.giftId)" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="save(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="primary" :disabled="!dirty.has(row.giftId)" @click="save(row)">保存</el-button>
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
const dirty = reactive(new Set())

async function load() {
  const vo = await post('/giftConfig/list')
  list.value = vo.data || []
  dirty.clear()
}

async function save(row) {
  await post('/giftConfig/update', { giftId: row.giftId, price: row.price, status: row.status })
  dirty.delete(row.giftId)
  ElMessage.success('已保存（缓存已失效，前台即时生效）')
}

onMounted(load)
</script>
