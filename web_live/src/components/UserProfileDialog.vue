<template>
  <el-dialog v-model="visible" title="个人设置" width="380px" :close-on-click-modal="false">
    <div class="profile-form">
      <!-- 头像 -->
      <div class="avatar-row">
        <img :src="avatar || defaultAvatar" class="avatar-preview" />
        <el-button size="small" :loading="avatarUploading" @click="pickFile">更换头像</el-button>
        <input ref="fileInputRef" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onFileChange" />
      </div>

      <!-- 昵称 -->
      <div class="field-label">昵称</div>
      <input v-model="nickName" class="nick-input" maxlength="20" placeholder="输入新的昵称" />
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { uploadImage } from '@/api/resource'
import { updateProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const userStore = useUserStore()
const defaultAvatar = 'data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2280%22%20height%3D%2280%22%3E%3Crect%20width%3D%22100%25%22%20height%3D%22100%25%22%20fill%3D%22%23667eea%22%2F%3E%3Ccircle%20cx%3D%2240%22%20cy%3D%2230%22%20r%3D%2214%22%20fill%3D%22%23ffffffaa%22%2F%3E%3Cpath%20d%3D%22M14%2074%20Q40%2048%2066%2074%20Z%22%20fill%3D%22%23ffffffaa%22%2F%3E%3C%2Fsvg%3E'
const nickName = ref('')
const avatar = ref('')
const avatarUploading = ref(false)
const saving = ref(false)
const fileInputRef = ref(null)

const canSave = computed(() => !avatarUploading.value && !saving.value &&
  (nickName.value.trim() !== userStore.userInfo.nickName || (avatar.value && avatar.value !== userStore.userInfo.avatar)))

// 每次打开时用当前用户信息初始化
watch(visible, (val) => {
  if (val) {
    nickName.value = userStore.userInfo.nickName || ''
    avatar.value = userStore.userInfo.avatar || ''
  }
})

function pickFile() {
  if (!avatarUploading.value) fileInputRef.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 5MB')
    return
  }
  avatarUploading.value = true
  try {
    const vo = await uploadImage(file)
    avatar.value = vo.data
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    avatarUploading.value = false
  }
}

async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    const data = {}
    if (nickName.value.trim() !== userStore.userInfo.nickName) data.nickName = nickName.value.trim()
    if (avatar.value && avatar.value !== userStore.userInfo.avatar) data.avatar = avatar.value
    await updateProfile(data)
    ElMessage.success('保存成功')
    await userStore.fetchUserInfo()
    visible.value = false
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-form { padding: 4px 2px; }
.avatar-row { display: flex; align-items: center; gap: 16px; }
.avatar-preview { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; border: 1px solid var(--sq-line); }
.field-label { font-size: 13px; color: #8a8aa0; margin: 18px 0 8px; }
.nick-input {
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
.nick-input:focus { border-color: var(--sq-blue); }
.nick-input::placeholder { color: #555; }
</style>
