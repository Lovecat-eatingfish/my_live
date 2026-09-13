<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">对账中心</h2>
      <div class="head-actions">
        <el-date-picker v-model="bizDate" type="date" placeholder="对账日期（可清空）" value-format="YYYY-MM-DD" clearable style="width: 200px" />
        <el-button @click="load">查询</el-button>
        <el-button type="primary" :loading="triggering" @click="handleTrigger">立即对账{{ bizDate ? `（${bizDate}）` : '（昨天）' }}</el-button>
      </div>
    </div>

    <!-- 签名元素：账实差值大数字卡 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">应收（订单侧）</div>
        <div class="stat-num num">{{ fmt(sum.expect) }}</div>
        <div class="stat-unit">金币</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">实收（流水侧）</div>
        <div class="stat-num num">{{ fmt(sum.actual) }}</div>
        <div class="stat-unit">金币</div>
      </div>
      <div class="stat-card" :class="{ diff: sum.diff !== 0 }">
        <div class="stat-label">账实差值</div>
        <div class="stat-num num">{{ sum.diff > 0 ? '+' : '' }}{{ fmt(sum.diff) }}</div>
        <div class="stat-unit">金币 · {{ sum.diff === 0 ? '平' : '不平' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">差错笔数</div>
        <div class="stat-num num">{{ list.length }}</div>
        <div class="stat-unit">条</div>
      </div>
    </div>

    <el-table :data="list" stripe class="recon-table">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="bizDate" label="对账日期" width="110" />
      <el-table-column label="差错类型" width="160">
        <template #default="{ row }">
          <span :class="['diff-tag', diffClass(row.diffType)]">{{ diffText(row.diffType) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="orderId" label="订单号" min-width="160">
        <template #default="{ row }">{{ row.orderId || '—' }}</template>
      </el-table-column>
      <el-table-column prop="userId" label="用户ID" width="120" />
      <el-table-column label="应收" width="90">
        <template #default="{ row }"><span class="num">{{ row.expectNum }}</span></template>
      </el-table-column>
      <el-table-column label="实收" width="90">
        <template #default="{ row }"><span class="num">{{ row.actualNum }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span :class="row.status === 0 ? 'st-open' : 'st-done'">{{ row.status === 0 ? '未处理' : '已处理' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" size="small" text type="primary" @click="openMark(row)">标记处理</el-button>
          <span v-else class="remark" :title="row.remark">{{ row.remark?.slice(0, 8) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="markVisible" title="标记差错已处理" width="420px">
      <el-input v-model="markRemark" type="textarea" :rows="3" placeholder="处理备注（如：已人工补账 / 已确认核销）" />
      <template #footer>
        <el-button @click="markVisible = false">取消</el-button>
        <el-button type="primary" @click="doMark">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { post } from '@/api'

const list = ref([])
const bizDate = ref('')
const triggering = ref(false)
const markVisible = ref(false)
const markRemark = ref('')
const markId = ref(null)

const sum = computed(() => {
  let expect = 0, actual = 0
  for (const row of list.value) {
    expect += Number(row.expectNum) || 0
    actual += Number(row.actualNum) || 0
  }
  return { expect, actual, diff: expect - actual }
})

const fmt = (n) => (Number(n) || 0).toLocaleString()
const diffText = (t) => ({ 1: '订单有流水无', 2: '流水有订单无', 3: '金额不平' }[t] || '未知')
const diffClass = (t) => ({ 1: 'd1', 2: 'd2', 3: 'd3' }[t] || 'd2')

async function load() {
  const vo = await post('/recon/list', { bizDate: bizDate.value || undefined, page: 1, pageSize: 100 })
  list.value = vo.data.list || []
}

async function handleTrigger() {
  triggering.value = true
  try {
    const vo = await post('/recon/trigger', { bizDate: bizDate.value || undefined })
    ElNotification({
      title: '对账完成',
      message: vo.data === 0 ? '账实相符，未发现差错' : `发现 ${vo.data} 条差错`,
      type: vo.data === 0 ? 'success' : 'warning'
    })
    await load()
  } finally {
    triggering.value = false
  }
}

function openMark(row) {
  markId.value = row.id
  markRemark.value = ''
  markVisible.value = true
}

async function doMark() {
  await post('/recon/mark', { id: markId.value, remark: markRemark.value || '已处理' })
  ElMessage.success('已标记处理')
  markVisible.value = false
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.page-title { margin: 0; font-size: 20px; }
.head-actions { display: flex; gap: 10px; }

.stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; margin-bottom: 22px; }
.stat-card {
  background: var(--surface); border: 1px solid var(--line); border-radius: 12px;
  padding: 18px 20px; position: relative;
}
.stat-card.diff { border-left: 4px solid var(--danger); }
.stat-label { font-size: 12px; color: var(--ink-2); }
.stat-num { font-size: 28px; font-weight: 700; margin-top: 6px; color: var(--ink); }
.stat-card.diff .stat-num { color: var(--danger); }
.stat-unit { font-size: 12px; color: var(--ink-2); margin-top: 2px; }

.recon-table { width: 100%; }
.diff-tag { font-size: 12px; padding: 2px 10px; border-radius: 4px; }
.diff-tag.d1 { color: var(--danger); background: #FBEDEB; }
.diff-tag.d2 { color: #B8860B; background: #FAF3E0; }
.diff-tag.d3 { color: #7D3C98; background: #F4ECF7; }
.st-open { color: var(--danger); font-size: 13px; }
.st-done { color: var(--brand); font-size: 13px; }
.remark { font-size: 12px; color: var(--ink-2); }
</style>
