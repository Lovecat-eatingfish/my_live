<template>
  <div class="wallet-page">
    <header class="nav-bar">
      <span class="back-btn" @click="$router.back()">← 返回</span>
      <span class="title">充值中心</span>
      <span class="balance">余额: {{ balance }} 币</span>
    </header>

    <div class="content">
      <div class="section-title">选择充值金额</div>
      <div class="product-grid">
        <div
          v-for="item in products"
          :key="item.id"
          :class="['product-card', { selected: selectedProduct?.id === item.id }]"
          @click="selectedProduct = item"
        >
          <div class="coin-icon">🪙</div>
          <div class="coin-num">{{ item.coinNum }} 币</div>
          <div class="product-name">{{ item.name }}</div>
        </div>
        <div v-if="products.length === 0" class="empty">暂无可用产品</div>
      </div>

      <div class="section-title" style="margin-top: 32px;">选择支付方式</div>
      <div class="channel-grid">
        <div
          v-for="ch in channels"
          :key="ch.value"
          :class="['channel-card', { selected: selectedChannel === ch.value }]"
          @click="selectedChannel = ch.value"
        >
          <span class="channel-icon">{{ ch.icon }}</span>
          <span class="channel-name">{{ ch.label }}</span>
        </div>
      </div>

      <div class="pay-btn-wrap">
        <el-button
          type="primary"
          size="large"
          :loading="paying"
          :disabled="!selectedProduct || !selectedChannel"
          @click="handlePay"
          class="pay-btn"
        >
          立即充值 {{ selectedProduct?.coinNum || 0 }} 币
        </el-button>
      </div>

      <div v-if="orderId" class="order-tip">
        订单已创建: {{ orderId }}，请完成支付...
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getProducts, payProduct } from '@/api/bank'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const products = ref([])
const selectedProduct = ref(null)
const selectedChannel = ref(1) // 默认微信
const balance = ref(0)
const paying = ref(false)
const orderId = ref('')

const channels = [
  { label: '微信支付', value: 1, icon: '💰' },
  { label: '支付宝', value: 2, icon: '💳' },
]

async function fetchProducts() {
  const vo = await getProducts(2) // 2 = 个人中心来源
  const list = vo.data?.payProductItemVOList || []
  products.value = list
  balance.value = vo.data?.currentBalance || 0
  if (list.length > 0) {
    selectedProduct.value = list[0]
  }
}

async function handlePay() {
  if (!selectedProduct.value) {
    ElMessage.warning('请选择充值产品')
    return
  }
  paying.value = true
  try {
    const vo = await payProduct({
      productId: selectedProduct.value.id,
      paySource: 2, // 个人中心
      payChannel: selectedChannel.value
    })
    orderId.value = vo.data?.orderId || ''
    if (orderId.value) {
      ElMessage.success('订单已创建，请完成支付')
    }
  } catch (e) {
    ElMessage.error('支付发起失败')
  } finally {
    paying.value = false
  }
}

onMounted(() => {
  fetchProducts()
})
</script>

<style scoped>
.wallet-page { min-height: 100vh; background: #0a0a0a; color: #fff; }
.nav-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; background: #161625; border-bottom: 1px solid #222;
}
.back-btn { color: #667eea; cursor: pointer; }
.title { font-size: 16px; font-weight: bold; }
.balance { color: #ffd700; font-size: 14px; }
.content { padding: 24px 20px; }
.section-title { font-size: 14px; color: #888; margin-bottom: 16px; }
.product-grid {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px;
}
.product-card {
  background: #1e1e2e; border: 2px solid transparent; border-radius: 12px;
  padding: 20px 12px; text-align: center; cursor: pointer; transition: all 0.2s;
}
.product-card:hover { border-color: #667eea; }
.product-card.selected { border-color: #667eea; background: rgba(102,126,234,0.15); }
.coin-icon { font-size: 28px; margin-bottom: 8px; }
.coin-num { font-size: 18px; font-weight: bold; color: #ffd700; margin-bottom: 4px; }
.product-name { font-size: 12px; color: #666; }
.channel-grid { display: flex; gap: 12px; }
.channel-card {
  flex: 1; display: flex; align-items: center; gap: 8px;
  background: #1e1e2e; border: 2px solid transparent; border-radius: 10px;
  padding: 14px; cursor: pointer; transition: all 0.2s;
}
.channel-card:hover { border-color: #667eea; }
.channel-card.selected { border-color: #667eea; background: rgba(102,126,234,0.15); }
.channel-icon { font-size: 20px; }
.channel-name { font-size: 14px; }
.pay-btn-wrap { margin-top: 32px; }
.pay-btn { width: 100%; height: 50px; font-size: 16px; }
.order-tip { text-align: center; color: #888; font-size: 13px; margin-top: 16px; }
.empty { grid-column: 1/-1; text-align: center; color: #444; padding: 40px 0; }
</style>
