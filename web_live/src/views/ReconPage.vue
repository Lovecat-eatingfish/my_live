<template>
  <div class="recon-page">
    <header class="nav-bar">
      <span class="back-btn" @click="$router.push('/')">← 返回</span>
      <span class="title">对账中心</span>
      <span class="sub">T+1 充值订单 ⇄ 金币流水 双向核对</span>
    </header>

    <div class="content">
      <!-- 统计卡片 -->
      <div class="stat-row">
        <div class="stat-card danger">
          <div class="stat-num">{{ countByType[1] }}</div>
          <div class="stat-label">订单有流水无（少入账）</div>
        </div>
        <div class="stat-card warn">
          <div class="stat-num">{{ countByType[2] }}</div>
          <div class="stat-label">流水有订单无（多入账）</div>
        </div>
        <div class="stat-card warn">
          <div class="stat-num">{{ countByType[3] }}</div>
          <div class="stat-label">金额不平</div>
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-date-picker
          v-model="bizDate"
          type="date"
          placeholder="选择对账日期（可清空查全部）"
          value-format="YYYY-MM-DD"
          clearable
          style="width: 240px"
        />
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button :loading="triggering" @click="handleTrigger">
          立即对账{{ bizDate ? `（${bizDate}）` : '（昨天）' }}
        </el-button>
        <span class="toolbar-hint">定时任务每天 01:30 自动核对前一自然日</span>
      </div>

      <!-- 差错明细表 -->
      <el-table :data="list" style="width: 100%" class="recon-table" empty-text="无差错记录，账实相符 🎉">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="bizDate" label="对账日期" width="110" />
        <el-table-column label="差错类型" width="150">
          <template #default="{ row }">
            <el-tag :type="tagType(row.diffType)">{{ diffTypeText(row.diffType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderId" label="订单号" min-width="150">
          <template #default="{ row }">{{ row.orderId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column label="应收金币" width="90">
          <template #default="{ row }">{{ row.expectNum ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="实收金币" width="90">
          <template #default="{ row }">{{ row.actualNum ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="处理状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'danger' : 'success'" effect="plain">
              {{ row.status === 0 ? '未处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发现时间" width="160" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { reconList, reconTrigger } from '@/api/bank'
import { ElMessage, ElNotification } from 'element-plus'

const list = ref([])
const bizDate = ref('')
const triggering = ref(false)

const countByType = computed(() => {
  const c = { 1: 0, 2: 0, 3: 0 }
  for (const item of list.value) c[item.diffType] = (c[item.diffType] || 0) + 1
  return c
})

const diffTypeText = (t) =>
  ({ 1: '订单有流水无', 2: '流水有订单无', 3: '金额不平' }[t] || `未知类型${t}`)
const tagType = (t) => ({ 1: 'danger', 2: 'warning', 3: 'warning' }[t] || 'info')

async function loadList() {
  const { data } = await reconList({ bizDate: bizDate.value || undefined, page: 1, pageSize: 50 })
  list.value = data?.list || []
}

async function handleTrigger() {
  triggering.value = true
  try {
    const { data } = await reconTrigger(bizDate.value || undefined)
    ElNotification({
      title: '对账完成',
      message: data === 0 ? '账实相符，未发现差错' : `发现 ${data} 条差错，已写入差错明细`,
      type: data === 0 ? 'success' : 'warning'
    })
    await loadList()
  } finally {
    triggering.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.recon-page {
  min-height: 100vh;
  background: #0f0f1a;
  color: #ddd;
}
.nav-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 24px;
  background: #161625;
  border-bottom: 1px solid #222;
  position: sticky;
  top: 0;
  z-index: 10;
}
.back-btn { cursor: pointer; color: #999; }
.back-btn:hover { color: #fff; }
.title { font-size: 18px; font-weight: bold; color: #fff; }
.sub { font-size: 12px; color: #666; }

.content { max-width: 1100px; margin: 0 auto; padding: 24px 16px; }

.stat-row { display: flex; gap: 16px; margin-bottom: 20px; }
.stat-card {
  flex: 1;
  background: #1a1a2e;
  border: 1px solid #26263a;
  border-radius: 10px;
  padding: 18px 20px;
  text-align: center;
}
.stat-card.danger { border-color: rgba(245, 108, 108, 0.4); }
.stat-card.warn { border-color: rgba(230, 162, 60, 0.4); }
.stat-num { font-size: 28px; font-weight: bold; color: #fff; }
.stat-card.danger .stat-num { color: #f56c6c; }
.stat-card.warn .stat-num { color: #e6a23c; }
.stat-label { font-size: 12px; color: #888; margin-top: 6px; }

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.toolbar-hint { font-size: 12px; color: #555; }

.recon-table {
  background: transparent;
}
:deep(.el-table) { --el-table-bg-color: #161625; --el-table-tr-bg-color: #161625;
  --el-table-header-bg-color: #1e1e2e; --el-table-row-hover-bg-color: #20203a;
  --el-table-border-color: #26263a; color: #ccc; }
</style>
