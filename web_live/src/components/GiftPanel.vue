<template>
  <el-dialog v-model="visible" title="选择礼物" width="520px" :close-on-click-modal="false">
    <div class="gift-grid">
      <div
        v-for="gift in giftList"
        :key="gift.giftId"
        :class="['gift-item', { selected: selected?.giftId === gift.giftId }]"
        @click="selected = gift"
      >
        <img :src="gift.coverImgUrl || defaultImg" class="gift-img" />
        <div class="gift-name">{{ gift.giftName }}</div>
        <div class="gift-price">💰 {{ gift.price }}</div>
      </div>
    </div>
    <div v-if="giftList.length === 0" class="empty">暂无可用礼物</div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selected" @click="handleConfirm">赠送</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { listGift } from '@/api/gift'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  roomId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'send'])

const giftList = ref([])
const selected = ref(null)
const defaultImg = 'data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2280%22%20height%3D%2280%22%3E%3Crect%20width%3D%22100%25%22%20height%3D%22100%25%22%20fill%3D%22%23667eea%22%2F%3E%3Ccircle%20cx%3D%2240%22%20cy%3D%2230%22%20r%3D%2214%22%20fill%3D%22%23ffffffaa%22%2F%3E%3Cpath%20d%3D%22M14%2074%20Q40%2048%2066%2074%20Z%22%20fill%3D%22%23ffffffaa%22%2F%3E%3C%2Fsvg%3E'

// 使用 computed 保持 visible 与 modelValue 同步（Vue 3 推荐写法）
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

watch(visible, async (val) => {
  if (val && giftList.value.length === 0) {
    await fetchGifts()
  }
})

async function fetchGifts() {
  try {
    const vo = await listGift()
    giftList.value = vo.data || []
    // 缓存到全局，供RoomPage礼物动画根据5556推送的url匹配礼物信息
    window.__qiyuGiftList = giftList.value
  } catch {
    ElMessage.error('获取礼物列表失败')
  }
}

function handleConfirm() {
  if (!selected.value) return
  emit('send', selected.value)
  visible.value = false
  selected.value = null
}
</script>

<style scoped>
.gift-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.gift-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 6px;
  border: 2px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  background: #0f0f1a;
}
.gift-item:hover { background: rgba(102,126,234,0.1); }
.gift-item.selected { border-color: var(--sq-blue); background: rgba(102,126,234,0.15); }
.gift-img { width: 52px; height: 52px; object-fit: contain; margin-bottom: 6px; }
.gift-name { font-size: 12px; color: #ddd; margin-bottom: 2px; text-align: center; }
.gift-price { font-size: 11px; color: #ffd700; }
.empty { text-align: center; color: #444; padding: 30px 0; }
</style>
