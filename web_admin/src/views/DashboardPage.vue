<template>
  <div>
    <div class="page-head"><h2 class="page-title">运营仪表盘（今日）</h2><el-button @click="load">刷新</el-button></div>
    <div class="cards">
      <div class="card">
        <div class="card-label">新增用户</div>
        <div class="card-value">{{ stats.newUsers ?? '-' }}</div>
      </div>
      <div class="card">
        <div class="card-label">开播场次</div>
        <div class="card-value">{{ stats.openRooms ?? '-' }}</div>
      </div>
      <div class="card">
        <div class="card-label">充值额（元）</div>
        <div class="card-value">{{ stats.rechargeYuan ?? '-' }}</div>
      </div>
      <div class="card">
        <div class="card-label">交易流水笔数</div>
        <div class="card-value">{{ stats.tradeCount ?? '-' }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { post } from '@/api'

const stats = ref({})

async function load() {
  const vo = await post('/stats/dashboard')
  stats.value = vo.data || {}
}

onMounted(load)
</script>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
.card-label { color: #888; font-size: 13px; }
.card-value { font-size: 30px; font-weight: bold; margin-top: 8px; color: #303133; }
</style>
