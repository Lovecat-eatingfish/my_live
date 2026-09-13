<template>
  <el-dialog v-model="visible" title="开播设置" width="440px" :close-on-click-modal="false">
    <div class="start-form">
      <!-- 封面上传 -->
      <div class="cover-label">直播间封面</div>
      <div class="cover-upload" @click="pickFile">
        <img v-if="coverUrl" :src="coverUrl" class="cover-preview" />
        <div v-else class="cover-empty">
          <div class="cover-empty-icon">🖼</div>
          <div class="cover-empty-text">点击上传封面</div>
          <div class="cover-empty-hint">建议 16:9 横图，jpg/png ≤ 5MB</div>
        </div>
        <div v-if="uploading" class="cover-uploading">上传中...</div>
      </div>
      <input ref="fileInputRef" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onFileChange" />

      <!-- 直播间名称 -->
      <div class="name-label">直播间名称</div>
      <input
        v-model="roomName"
        class="name-input"
        placeholder="给直播间起个吸引人的名字吧"
        maxlength="30"
        @keyup.enter="handleConfirm"
      />
      <div class="name-count">{{ roomName.length }}/30</div>

      <!-- 直播间类型 -->
      <div class="name-label">直播类型</div>
      <div class="type-options">
        <span
          v-for="t in livingTypes"
          :key="t.value"
          :class="['type-option', { active: livingType === t.value }]"
          @click="livingType = t.value"
        >{{ t.label }}</span>
      </div>

      <!-- 门票设置 -->
      <div class="name-label">付费门票</div>
      <div class="ticket-row">
        <el-switch v-model="ticketEnabled" />
        <template v-if="ticketEnabled">
          <input v-model.number="ticketPrice" type="number" min="1" class="ticket-input" />
          <span class="ticket-unit">金币/人</span>
        </template>
      </div>

      <div class="form-hint">观众会在直播列表看到你的封面和名称，认真填更容易被看到哦</div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :loading="submitting" :disabled="!canSubmit" @click="handleConfirm">
        {{ submitting ? '开播中...' : '确认开播' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { uploadImage } from '@/api/resource'
import { listMyShop } from '@/api/gift'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'confirm', 'goShopManage'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const roomName = ref('')
const ticketEnabled = ref(false)
const ticketPrice = ref(100)
const coverUrl = ref('')
const uploading = ref(false)
const submitting = ref(false)
const fileInputRef = ref(null)
const livingType = ref(1)
// 与首页筛选 tab 对齐的类型编码：1娱乐(推荐) 2游戏 3赛事 4带货
const livingTypes = [
  { label: '娱乐', value: 1 },
  { label: '游戏', value: 2 },
  { label: '赛事', value: 3 },
  { label: '带货', value: 4 },
]

const canSubmit = computed(() => roomName.value.trim().length > 0 && coverUrl.value && !uploading.value)

// 每次打开重置上次的输入
watch(visible, (val) => {
  if (val) {
    roomName.value = ''
    coverUrl.value = ''
    livingType.value = 1
  }
})

function pickFile() {
  if (!uploading.value) fileInputRef.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('封面图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const vo = await uploadImage(file)
    coverUrl.value = vo.data
    ElMessage.success('封面上传成功')
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    uploading.value = false
  }
}

async function handleConfirm() {
  if (!roomName.value.trim()) {
    ElMessage.warning('先给直播间起个名字')
    return
  }
  if (!coverUrl.value) {
    ElMessage.warning('先上传一张直播间封面')
    return
  }
  // 带货类型：先校验已上架商品，为空则引导去配置
  if (livingType.value === 4) {
    try {
      const vo = await listMyShop()
      if (!(vo.data || []).length) {
        ElMessageBox.confirm('带货直播需要先在商品管理上架至少一件商品，现在去配置吗？', '还没有配置商品', {
          confirmButtonText: '去配置商品',
          cancelButtonText: '返回',
          type: 'warning'
        }).then(() => emit('goShopManage')).catch(() => {})
        return
      }
    } catch {
      return // 查询失败不拦截开播（后端会再兜底校验）
    }
  }
  if (submitting.value) return
  submitting.value = true
  emit('confirm', {
    roomName: roomName.value.trim(), covertImg: coverUrl.value, type: livingType.value,
    payType: ticketEnabled.value ? 1 : 0, ticketPrice: ticketEnabled.value ? Number(ticketPrice.value) : null
  })
}

// 父组件开播成功后调用，关闭弹窗并复位
function finish() {
  submitting.value = false
  visible.value = false
}

function fail() {
  submitting.value = false
}

defineExpose({ finish, fail })
</script>

<style scoped>
.start-form { padding: 4px 2px; }
.cover-label, .name-label { font-size: 13px; color: #8a8aa0; margin-bottom: 8px; }
.name-label { margin-top: 18px; }
.cover-upload {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 1px dashed var(--sq-line);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  background: #12121f;
  transition: border-color 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cover-upload:hover { border-color: var(--sq-blue); }
.cover-preview { width: 100%; height: 100%; object-fit: cover; display: block; }
.cover-empty { text-align: center; color: #555; }
.cover-empty-icon { font-size: 34px; margin-bottom: 8px; }
.cover-empty-text { font-size: 14px; color: #888; }
.cover-empty-hint { font-size: 12px; color: #444; margin-top: 4px; }
.cover-uploading {
  position: absolute; inset: 0;
  background: rgba(0,0,0,0.55);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 14px;
}
.name-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--sq-line);
  border-radius: 10px;
  background: var(--sq-card);
  color: #fff;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}
.name-input:focus { border-color: var(--sq-blue); }
.name-input::placeholder { color: #555; }
.name-count { text-align: right; font-size: 12px; color: #555; margin-top: 4px; }
.type-options { display: flex; gap: 10px; margin-bottom: 6px; }
.type-option {
  padding: 7px 18px;
  border: 1px solid var(--sq-line);
  border-radius: 18px;
  font-size: 13px;
  color: #999;
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}
.type-option:hover { border-color: var(--sq-blue); color: #ccc; }
.type-option.active { background: linear-gradient(135deg, var(--sq-blue), #764ba2); border-color: var(--sq-blue); color: #fff; }
.form-hint { font-size: 12px; color: #666; margin-top: 14px; line-height: 1.6; }
</style>
