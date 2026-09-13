<template>
  <el-dialog v-model="visible" title="商品管理（小黄车上架）" width="460px" :close-on-click-modal="false">
    <div class="shop-manage-tip">勾选的商品会出现在直播间小黄车，观众可加购、下单</div>
    <div v-loading="loading" class="sku-list">
      <div v-for="sku in skus" :key="sku.skuId" class="sku-item" @click="toggle(sku)">
        <el-checkbox :model-value="onShelfIds.has(sku.skuId)" @change="toggle(sku)" @click.stop />
        <img :src="sku.iconUrl" class="sku-icon" />
        <div class="sku-info">
          <div class="sku-name">{{ sku.name }}</div>
          <div class="sku-price">¥{{ (sku.skuPrice / 100).toFixed(2) }}</div>
        </div>
        <span :class="['shelf-tag', onShelfIds.has(sku.skuId) ? 'on' : 'off']">
          {{ onShelfIds.has(sku.skuId) ? '已上架' : '未上架' }}
        </span>
      </div>
      <div v-if="!loading && skus.length === 0" class="empty">暂无可上架商品</div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { listAllSkus, listMyShop, updateShopStatus } from '@/api/gift'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const skus = ref([])
const onShelfIds = ref(new Set())
const loading = ref(false)

// 每次打开都刷新最新数据
watch(visible, async (val) => {
  if (!val) return
  loading.value = true
  try {
    const [allVo, myVo] = await Promise.all([listAllSkus(), listMyShop()])
    skus.value = allVo.data || []
    onShelfIds.value = new Set((myVo.data || []).map(s => s.skuId))
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    loading.value = false
  }
})

async function toggle(sku) {
  const nextStatus = onShelfIds.value.has(sku.skuId) ? 0 : 1
  try {
    await updateShopStatus({ skuId: sku.skuId, status: nextStatus })
    const newSet = new Set(onShelfIds.value)
    if (nextStatus === 1) {
      newSet.add(sku.skuId)
      ElMessage.success(`「${sku.name}」已上架`)
    } else {
      newSet.delete(sku.skuId)
      ElMessage.info(`「${sku.name}」已下架`)
    }
    onShelfIds.value = newSet
  } catch {
    // 错误提示由拦截器统一处理
  }
}
</script>

<style scoped>
.shop-manage-tip { font-size: 12px; color: #888; margin-bottom: 12px; }
.sku-list { max-height: 380px; overflow-y: auto; }
.sku-item {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px; border: 1px solid var(--sq-line); border-radius: 10px;
  margin-bottom: 8px; cursor: pointer; transition: border-color 0.15s;
}
.sku-item:hover { border-color: var(--sq-blue); }
.sku-icon { width: 42px; height: 42px; border-radius: 8px; object-fit: cover; background: var(--sq-card); }
.sku-info { flex: 1; }
.sku-name { font-size: 14px; color: #eee; }
.sku-price { font-size: 13px; color: #ffd700; margin-top: 2px; }
.shelf-tag { font-size: 12px; padding: 2px 10px; border-radius: 10px; }
.shelf-tag.on { color: #67c23a; background: rgba(103, 194, 58, 0.12); }
.shelf-tag.off { color: #888; background: rgba(255, 255, 255, 0.06); }
.empty { text-align: center; color: #555; padding: 30px 0; font-size: 13px; }
</style>
