<template>
  <div>
    <div class="page-head"><h2 class="page-title">充值档位</h2><el-button @click="load">刷新</el-button></div>
    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="档位名称" min-width="140" />
      <el-table-column label="价格（元）" width="160">
        <template #default="{ row }">
          <el-input-number v-model="row.priceYuan" :min="0.01" :precision="2" :step="1" size="small"
            @change="dirty.add(row.id)" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-switch v-model="row.validStatus" :active-value="1" :inactive-value="0" @change="save(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="primary" :disabled="!dirty.has(row.id)" @click="save(row)">保存</el-button>
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
  const vo = await post('/payProduct/list')
  list.value = (vo.data || []).map(p => ({ ...p, priceYuan: (Number(p.price) || 0) / 100 }))
  dirty.clear()
}

async function save(row) {
  const price = Math.round(Number(row.priceYuan) * 100)
  await post('/payProduct/update', { productId: row.id, price, validStatus: row.validStatus })
  dirty.delete(row.id)
  ElMessage.success('已保存')
}

onMounted(load)
</script>
