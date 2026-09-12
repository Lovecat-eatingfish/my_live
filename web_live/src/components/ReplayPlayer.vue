<template>
  <div class="replay-player-wrap">
    <video ref="videoRef" controls preload="metadata" class="replay-video-el"></video>
    <div v-if="error" class="player-error">⚠️ {{ error }}</div>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps({
  /** 回放地址：.flv 用 flv.js 播放，其余(mp4 等)走原生 video */
  src: { type: String, default: '' }
})

const videoRef = ref(null)
const error = ref('')
let flvPlayer = null

function initPlayer(src) {
  destroyPlayer()
  error.value = ''
  if (!videoRef.value || !src) return
  if (src.toLowerCase().includes('.flv')) {
    // SRS 4 DVR 录制的是 FLV，浏览器原生不支持，用 flv.js 解封装播放
    import('flv.js').then((mod) => {
      const flvjs = mod.default
      if (!flvjs.isSupported()) {
        error.value = '当前浏览器不支持 FLV 播放'
        return
      }
      flvPlayer = flvjs.createPlayer({
        type: 'flv',
        url: src,
        isLive: false
      }, {
        enableWorker: false,
        enableStashBuffer: false,
        stashInitialSize: 128
      })
      flvPlayer.attachMediaElement(videoRef.value)
      flvPlayer.on(flvjs.Events.ERROR, () => {
        error.value = '回放加载失败，请稍候重试'
      })
      flvPlayer.load()
    }).catch(() => {
      error.value = '播放组件加载失败'
    })
  }
  // 其他格式（mp4 等）原生 video 直接播放，无需处理
}

function destroyPlayer() {
  if (flvPlayer) {
    try {
      flvPlayer.pause()
      flvPlayer.unload()
      flvPlayer.detachMediaElement()
      flvPlayer.destroy()
    } catch { /* 已销毁 */ }
    flvPlayer = null
  }
}

watch(() => props.src, (val) => {
  if (val) initPlayer(val)
  else destroyPlayer()
})

onUnmounted(destroyPlayer)
</script>

<style scoped>
.replay-player-wrap {
  position: relative;
  width: 100%;
}
.replay-video-el {
  width: 100%;
  border-radius: 8px;
  background: #000;
}
.player-error {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: 6px 10px;
  color: #ffb347;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.7);
}
</style>
