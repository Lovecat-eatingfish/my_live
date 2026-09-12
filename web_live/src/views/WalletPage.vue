<template>
  <div class="wallet-page">
    <header class="nav-bar">
      <span class="back-btn" @click="$router.back()">← 返回</span>
      <span class="title">充值中心</span>
      <span class="balance">🪙 {{ formatBalance(userStore.balance) }} 金币</span>
    </header>

    <div class="content">
      <!-- 余额大字卡片 -->
      <div class="balance-card">
        <div class="balance-label">当前余额（金币）</div>
        <div class="balance-num">{{ formatBalance(userStore.balance) }}</div>
        <div class="balance-sub">充值比例 1元 = 100金币，支付后立即到账</div>
      </div>

      <div class="section-title">选择充值档位</div>
      <div class="product-grid">
        <div
          v-for="item in products"
          :key="item.id"
          :class="['product-card', { selected: selectedProduct?.id === item.id }]"
          @click="selectedProduct = item"
        >
          <span v-if="item.id === recommendId" class="recommend-badge">推荐</span>
          <div class="coin-icon">🪙</div>
          <div class="coin-num">{{ item.coinNum }}</div>
          <div class="product-price">¥{{ (item.price / 100).toFixed(0) }}</div>
        </div>
        <div v-if="products.length === 0" class="empty">暂无可用档位</div>
      </div>

      <div class="pay-btn-wrap">
        <el-button
          type="primary"
          size="large"
          round
          :loading="paying"
          :disabled="!selectedProduct"
          @click="handlePay"
          class="pay-btn"
        >
          立即充值 ¥{{ selectedProduct ? (selectedProduct.price / 100).toFixed(0) : '--' }}
        </el-button>
        <div class="pay-hint">本项目为模拟支付：点击后自动完成「下单 → 支付回调 → 金币入账」全流程</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getProducts, payProduct } from '@/api/bank'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElNotification } from 'element-plus'

const userStore = useUserStore()
const products = ref([])
const selectedProduct = ref(null)
const paying = ref(false)

// 推荐：按 1元=100金币 比例，加赠比例最高的那一档
const recommendId = computed(() => {
  let best = null
  let bestRatio = 0
  for (const p of products.value) {
    const ratio = p.coinNum / p.price
    if (ratio > bestRatio) { bestRatio = ratio; best = p.id }
  }
  return best
})

const formatBalance = (n) => (Number(n) || 0).toLocaleString()

async function fetchProducts() {
  const vo = await getProducts(0) // 0 = 旗鱼金币产品类型
  const list = vo.data?.payProductItemVOList || []
  products.value = list
  if (list.length > 0) {
    selectedProduct.value = list.find(p => p.id === recommendId.value) || list[0]
  }
}

async function handlePay() {
  if (!selectedProduct.value || paying.value) return
  paying.value = true
  try {
    const vo = await payProduct({
      productId: selectedProduct.value.id,
      paySource: 2, // 个人中心
      payChannel: 1 // 微信渠道（模拟）
    })
    const orderId = vo.data?.orderId || ''
    // 后端在下单后直接模拟支付回调，到这里金币已经到账
    await userStore.refreshBalance()
    ElNotification({
      title: '充值成功',
      message: `+${selectedProduct.value.coinNum} 金币已到账${orderId ? `（订单 ${orderId}）` : ''}`,
      type: 'success',
      duration: 3000
    })
  } catch (e) {
    ElMessage.error(e?.msg || '充值失败，请稍后重试')
  } finally {
    paying.value = false
  }
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  await userStore.refreshBalance()
  await fetchProducts()
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
.content { max-width: 720px; margin: 0 auto; padding: 24px 20px 60px; }

.balance-card {
  background: linear-gradient(135deg, #2b2350, #1a1a2e);
  border: 1px solid #3d3768;
  border-radius: 16px;
  padding: 28px 32px;
  text-align: center;
}
.balance-label { font-size: 13px; color: #8a8aa0; }
.balance-num { font-size: 40px; font-weight: bold; color: #ffd700; margin: 8px 0; }
.balance-sub { font-size: 12px; color: #666; }

.section-title { font-size: 14px; color: #888; margin: 28px 0 16px; }
.product-grid {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px;
}
.product-card {
  position: relative;
  background: #1e1e2e; border: 2px solid transparent; border-radius: 14px;
  padding: 22px 12px 18px; text-align: center; cursor: pointer; transition: all 0.2s;
}
.product-card:hover { border-color: #667eea; }
.product-card.selected { border-color: #667eea; background: rgba(102,126,234,0.15); }
.recommend-badge {
  position: absolute; top: -1px; right: -1px;
  background: linear-gradient(135deg, #ff7a45, #f5222d);
  font-size: 11px; padding: 2px 10px;
  border-radius: 0 12px 0 10px;
}
.coin-icon { font-size: 26px; margin-bottom: 6px; }
.coin-num { font-size: 18px; font-weight: bold; color: #ffd700; margin-bottom: 4px; }
.product-price { font-size: 13px; color: #aaa; }

.pay-btn-wrap { margin-top: 36px; }
.pay-btn { width: 100%; height: 50px; font-size: 16px; }
.pay-hint { text-align: center; color: #555; font-size: 12px; margin-top: 12px; }
.empty { grid-column: 1/-1; text-align: center; color: #444; padding: 40px 0; }
</style>
