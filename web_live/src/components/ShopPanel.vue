<template>
  <el-drawer
    :model-value="modelValue"
    title="🛍 直播带货"
    size="360px"
    direction="rtl"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="loadShop"
  >
    <!-- 商品列表 / 购物车 两个页签 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="商品" name="goods">
        <div v-if="loading" class="shop-empty">加载中...</div>
        <div v-else-if="!skuList.length" class="shop-empty">
          主播还没上架商品
        </div>
        <div v-else class="shop-list">
          <div v-for="sku in skuList" :key="sku.skuId" class="shop-item">
            <img :src="sku.iconUrl || defaultIcon" class="shop-icon" />
            <div class="shop-info">
              <div class="shop-name">{{ sku.name }}</div>
              <div class="shop-remark">{{ sku.remark || '主播精选好物' }}</div>
              <div class="shop-price-row">
                <span class="shop-price">¥{{ priceYuan(sku.skuPrice) }}</span>
                <div class="shop-btns">
                  <el-button size="small" plain @click="addToCart(sku)">加购</el-button>
                  <el-button size="small" type="danger" round @click="buy(sku)">购买</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="`购物车${cartTotalCount ? '(' + cartTotalCount + ')' : ''}`" name="cart">
        <div v-if="cartLoading" class="shop-empty">加载中...</div>
        <div v-else-if="!cartItems.length" class="shop-empty">购物车空空如也</div>
        <template v-else>
          <div class="shop-list">
            <div v-for="item in cartItems" :key="item.skuId" class="shop-item">
              <img :src="item.iconUrl || defaultIcon" class="shop-icon" />
              <div class="shop-info">
                <div class="shop-name">{{ item.name }}</div>
                <div class="shop-price">¥{{ priceYuan(item.skuPrice) }}</div>
                <div class="cart-num-row">
                  <el-button size="small" circle @click="changeNum(item, item.num - 1)">-</el-button>
                  <span class="cart-num">{{ item.num }}</span>
                  <el-button size="small" circle @click="changeNum(item, item.num + 1)">+</el-button>
                  <el-button size="small" link type="danger" @click="removeItem(item)">删除</el-button>
                </div>
              </div>
            </div>
          </div>
          <div class="cart-footer">
            <div class="cart-total">
              合计：<span class="shop-price">¥{{ priceYuan(cartTotalPrice) }}</span>
            </div>
            <el-button type="danger" round :loading="checkingOut" @click="checkout">去结算</el-button>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listShop, createOrder, payOrder, cartAdd, cartUpdate, cartRemove, cartList, cartCheckout } from '@/api/gift'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  roomId: { type: Number, required: true }
})
const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const skuList = ref([])
const activeTab = ref('goods')
const cartItems = ref([])
const cartLoading = ref(false)
const checkingOut = ref(false)
const defaultIcon = 'https://via.placeholder.com/80/667eea/fff?text=%E5%95%86'

const cartTotalCount = computed(() => cartItems.value.reduce((sum, item) => sum + item.num, 0))
const cartTotalPrice = computed(() => cartItems.value.reduce((sum, item) => sum + item.num * item.skuPrice, 0))

async function loadShop() {
  loading.value = true
  try {
    const vo = await listShop(props.roomId)
    skuList.value = vo.data || []
  } catch {
    skuList.value = []
  } finally {
    loading.value = false
  }
  if (activeTab.value === 'cart') await loadCart()
}

async function loadCart() {
  cartLoading.value = true
  try {
    const vo = await cartList(props.roomId)
    cartItems.value = vo.data?.items || []
  } catch {
    cartItems.value = []
  } finally {
    cartLoading.value = false
  }
}

function onTabChange(tab) {
  if (tab === 'cart') loadCart()
}

async function addToCart(sku) {
  try {
    const vo = await cartAdd({ roomId: props.roomId, skuId: sku.skuId, num: 1 })
    cartItems.value = vo.data || []
    ElMessage.success('已加入购物车')
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}

async function changeNum(item, num) {
  try {
    const vo = await cartUpdate({ roomId: props.roomId, skuId: item.skuId, num })
    cartItems.value = vo.data || []
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}

async function removeItem(item) {
  try {
    const vo = await cartRemove({ roomId: props.roomId, skuId: item.skuId })
    cartItems.value = vo.data || []
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}

// 结算：购物车全部商品创建订单并支付，支付成功购物车清空
async function checkout() {
  if (checkingOut.value || !cartItems.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定结算购物车全部商品？合计 ¥${priceYuan(cartTotalPrice.value)}`,
      '确认订单',
      { confirmButtonText: '支付', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  checkingOut.value = true
  try {
    const vo = await cartCheckout(props.roomId)
    const data = vo.data || {}
    if (!data.paySuccess) {
      ElMessage.warning('余额不足，订单已创建，请先充值后到订单列表支付')
      return
    }
    cartItems.value = []
    ElMessage.success('购买成功！可在订单列表查看')
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    checkingOut.value = false
  }
}

function priceYuan(price) {
  return ((Number(price) || 0) / 100).toFixed(2)
}

// 购买：创建订单 -> 模拟支付 -> 成功提示
async function buy(sku) {
  try {
    await ElMessageBox.confirm(
      `确定购买「${sku.name}」？价格 ¥${priceYuan(sku.skuPrice)}`,
      '确认订单',
      { confirmButtonText: '支付', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  try {
    const orderVO = await createOrder({ skuIdList: String(sku.skuId), roomId: props.roomId })
    const order = orderVO.data || {}
    if (!order.orderId) throw new Error('订单创建失败')
    await payOrder({ orderId: order.orderId })
    ElMessage.success('购买成功！可在订单列表查看')
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  }
}
</script>

<style scoped>
.shop-empty {
  text-align: center;
  color: #999;
  padding: 60px 0;
}
.shop-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.shop-item {
  display: flex;
  gap: 12px;
  padding: 10px;
  border: 1px solid #eee;
  border-radius: 10px;
}
.shop-icon {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}
.shop-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.shop-name {
  font-size: 14px;
  font-weight: bold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-remark {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.shop-btns {
  display: flex;
  gap: 6px;
}
.shop-price {
  color: #ee0a24;
  font-size: 18px;
  font-weight: bold;
}
.cart-num-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.cart-num {
  min-width: 24px;
  text-align: center;
  font-weight: bold;
}
.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 4px 0;
  border-top: 1px solid #eee;
  margin-top: 6px;
}
.cart-total {
  font-size: 14px;
}
</style>
