<template>
  <el-dialog v-model="visible" title="发布视频" width="480px" :close-on-click-modal="false">
    <div class="publish-form">
      <!-- 视频文件 -->
      <div v-if="!videoUrl" class="video-pick" @click="pickVideo">
        <template v-if="videoUploading">
          <div class="pick-icon">📤</div>
          <el-progress :percentage="progress" :stroke-width="10" style="width: 80%" />
          <div class="pick-hint">上传中 {{ progress }}%（大文件请耐心等待）</div>
          <el-button size="small" style="margin-top: 8px" @click.stop="cancelUpload">取消上传</el-button>
        </template>
        <template v-else>
          <div class="pick-icon">🎥</div>
          <div class="pick-text">点击选择视频文件</div>
          <div class="pick-hint">支持 MP4/WebM/MOV，≤ 300MB</div>
        </template>
      </div>
      <div v-else class="video-done">
        ✅ 视频已上传（{{ videoSizeMb }}MB{{ duration ? ` · ${formatDuration(duration)}` : '' }}）
        <span class="re-pick" @click="pickVideo">重选</span>
      </div>
      <input ref="videoInputRef" type="file" accept="video/mp4,video/webm,video/quicktime" hidden @change="onVideoChange" />

      <!-- 封面预览（自动截取首帧） -->
      <div class="cover-row">
        <img v-if="coverUrl" :src="coverUrl" class="cover-thumb" />
        <div v-else class="cover-thumb cover-empty">封面自动截取中...</div>
        <div class="cover-tip">封面取自视频首帧</div>
      </div>

      <!-- 标题 -->
      <input v-model="title" class="title-input" placeholder="起个标题吧" maxlength="60" />
      <!-- 标签 -->
      <div class="tag-row">
        <span
          v-for="t in tags"
          :key="t.id"
          :class="['tag-chip', { active: tagId === t.id }]"
          @click="tagId = t.id"
        >{{ t.tagName }}</span>
      </div>
    </div>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="danger" :loading="publishing" :disabled="!canPublish" @click="handlePublish">
        发布
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { uploadVideo, publishVideo, listVideoTags } from '@/api/video'
import { uploadImage } from '@/api/resource'
import { ElMessage, ElMessageBox } from 'element-plus'

const emit = defineEmits(['update:modelValue', 'published'])

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const videoUrl = ref('')
const videoSizeMb = ref(0)
const duration = ref(0)
const coverUrl = ref('')
const title = ref('')
const tagId = ref(0)
const tags = ref([])
const videoUploading = ref(false)
const publishing = ref(false)
const videoInputRef = ref(null)
const videoFile = ref(null)
const videoXhrRef = ref(null)
const progress = ref(0)

const canPublish = computed(() => videoUrl.value && title.value.trim() && tagId.value > 0 && !videoUploading.value)

const formatDuration = (s) => {
  const m = Math.floor(s / 60), r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}

watch(visible, async (val) => {
  if (val) {
    title.value = ''
    videoUrl.value = ''
    coverUrl.value = ''
    tagId.value = 0
    duration.value = 0
    videoFile.value = null
    if (tags.value.length === 0) {
      try {
        const vo = await listVideoTags()
        tags.value = (vo.data || []).filter(t => t.tagName !== '全部')
      } catch { /* 忽略 */ }
    }
  }
})

function pickVideo() {
  if (!videoUploading.value) videoInputRef.value?.click()
}

async function onVideoChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.size > 300 * 1024 * 1024) {
    ElMessage.warning('视频不能超过 300MB')
    return
  }
  videoFile.value = file
  videoSizeMb.value = (file.size / 1024 / 1024).toFixed(1)
  videoUploading.value = true
  progress.value = 0
  try {
    // 上传前先从本地文件抽首帧做封面（不阻塞上传）
    captureCover(file)
    const vo = await uploadVideo(file, {
      onProgress: (p) => { progress.value = p },
      xhrRef: videoXhrRef
    })
    videoUrl.value = vo.data
    ElMessage.success('视频上传成功')
  } catch (e) {
    if (e?.aborted) {
      // 用户主动取消上传：复位选择状态，可直接重选
      videoFile.value = null
    }
    // 其他失败：错误提示已在 XHR 层弹出，保留已选文件便于重试
  } finally {
    videoUploading.value = false
    videoXhrRef.value = null
  }
}

/** 中止当前上传（onabort 会 reject，由 onVideoChange 的 catch/finally 统一复位） */
function abortUpload() {
  videoXhrRef.value?.abort()
}

const cancelUpload = abortUpload

/** 取消按钮：上传中需确认中断，空闲时直接关闭 */
function handleCancel() {
  if (!videoUploading.value) {
    visible.value = false
    return
  }
  ElMessageBox.confirm('视频正在上传，关闭将中断本次上传', '提示', {
    confirmButtonText: '中断并关闭',
    cancelButtonText: '继续上传',
    type: 'warning'
  }).then(() => {
    abortUpload()
    visible.value = false
  }).catch(() => { /* 留在弹窗继续上传 */ })
}

// 弹窗以任意方式关闭（ESC/右上角×）时也要中止上传，避免挂起请求残留
watch(visible, (val) => {
  if (!val && videoUploading.value) abortUpload()
})

/** 用 <video> + canvas 抽首帧上传为封面；失败不阻塞发布 */
function captureCover(file) {
  try {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.muted = true
    video.preload = 'metadata'
    video.src = url
    video.onloadedmetadata = () => {
      duration.value = Math.round(video.duration) || 0
      video.currentTime = Math.min(0.5, video.duration / 2)
    }
    video.onseeked = () => {
      const canvas = document.createElement('canvas')
      canvas.width = 640
      canvas.height = Math.round(640 * (video.videoHeight / video.videoWidth)) || 360
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      canvas.toBlob(async (blob) => {
        URL.revokeObjectURL(url)
        if (!blob) return
        try {
          const vo = await uploadImage(new File([blob], 'cover.jpg', { type: 'image/jpeg' }))
          coverUrl.value = vo.data
        } catch { /* 封面上传失败可无封面发布 */ }
      }, 'image/jpeg', 0.85)
    }
  } catch { /* 忽略抽帧失败 */ }
}

async function handlePublish() {
  if (!title.value.trim()) { ElMessage.warning('先填标题'); return }
  if (tagId.value <= 0) { ElMessage.warning('选一个标签'); return }
  if (publishing.value) return
  publishing.value = true
  try {
    const vo = await publishVideo({
      title: title.value.trim(),
      tagId: tagId.value,
      videoUrl: videoUrl.value,
      coverUrl: coverUrl.value,
      duration: duration.value,
      size: videoFile.value?.size || 0
    })
    ElMessage.success('发布成功')
    emit('published', vo.data)
  } catch {
    // 错误由拦截器提示
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.publish-form { display: flex; flex-direction: column; gap: 14px; }
.video-pick {
  border: 1px dashed var(--sq-line); border-radius: 12px;
  padding: 28px 0; text-align: center; cursor: pointer;
  background: #12121f; transition: border-color 0.2s;
}
.video-pick:hover { border-color: var(--sq-blue); }
.pick-icon { font-size: 32px; margin-bottom: 8px; }
.pick-text { font-size: 14px; color: #888; }
.pick-hint { font-size: 12px; color: #444; margin-top: 4px; }
.video-done { font-size: 13px; color: #6ee7b7; background: #12241c; border-radius: 8px; padding: 12px 14px; }
.re-pick { color: var(--sq-blue); cursor: pointer; margin-left: 8px; }
.cover-row { display: flex; align-items: center; gap: 12px; }
.cover-thumb {
  width: 128px; height: 72px; object-fit: cover; border-radius: 8px;
  background: #12121f; border: 1px solid #2c2c44;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: #555;
}
.cover-empty { color: #555; }
.cover-tip { font-size: 12px; color: #555; }
.title-input {
  width: 100%; box-sizing: border-box;
  padding: 10px 14px; border: 1px solid var(--sq-line); border-radius: 10px;
  background: var(--sq-card); color: #fff; font-size: 14px; outline: none;
}
.title-input:focus { border-color: var(--sq-blue); }
.title-input::placeholder { color: #555; }
.tag-row { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-chip {
  padding: 4px 14px; border-radius: 14px; font-size: 13px;
  color: #888; background: var(--sq-card); cursor: pointer; border: 1px solid transparent;
}
.tag-chip.active { color: #fff; background: var(--sq-blue); }
</style>
