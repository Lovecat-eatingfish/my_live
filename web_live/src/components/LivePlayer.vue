<template>
  <div class="live-player-wrap">
    <video ref="videoRef" autoplay muted playsinline :class="{ 'vjs-style': mode === 'hls' }"></video>
    <div v-if="statusText" class="player-status">
      <span>⚠️ {{ statusText }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  /** HLS 兜底地址（WebRTC 失败或不可用时使用） */
  src: { type: String, default: '' },
  /** WebRTC 播放信令接口（同源相对路径），与 rtcStream 同时提供时启用 WebRTC 播放 */
  rtcApi: { type: String, default: '' },
  /** WebRTC 流地址 webrtc://host:port/live/streamKey */
  rtcStream: { type: String, default: '' }
})

const videoRef = ref(null)
const mode = ref(null) // 'rtc' | 'hls'
const statusText = ref('')

const MAX_RTC_RETRY = 6
const RTC_RETRY_INTERVAL = 3000
const MAX_HLS_RETRY = 20

let rtcPc = null
let rtcRetryTimer = null
let rtcRetryCount = 0
let rtcStreamObj = null

let hlsPlayer = null
let hlsRetryTimer = null
let hlsRetryCount = 0

let stopped = false
let currentToken = 0 // 防止模式切换后旧异步流程污染新会话

// ==================== WebRTC 播放（主通道） ====================

async function startRtc(token) {
  if (stopped || token !== currentToken) return
  if (!videoRef.value) return
  teardownRtc()
  statusText.value = '连接主播画面中...'
  try {
    const pc = new RTCPeerConnection()
    rtcPc = pc
    pc.addTransceiver('video', { direction: 'recvonly' })
    pc.addTransceiver('audio', { direction: 'recvonly' })
    pc.ontrack = (e) => {
      if (videoRef.value && e.streams[0]) {
        rtcStreamObj = e.streams[0]
        videoRef.value.srcObject = rtcStreamObj
        // 浏览器自动播放策略：未静音的自动播放会被拦截导致黑屏，
        // 先静音起播（muted autoplay 各浏览器均放行），起播成功后尝试恢复声音
        const v = videoRef.value
        v.play().then(() => {
          v.muted = false
        }).catch(() => { v.muted = true })
      }
    }
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)

    const res = await fetch(props.rtcApi, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ api: location.origin + props.rtcApi, streamurl: props.rtcStream, sdp: offer.sdp })
    })
    const vo = await res.json()
    if (vo.code !== 0 || !vo.sdp) throw new Error('SRS 信令失败 code=' + vo.code)
    await pc.setRemoteDescription({ type: 'answer', sdp: vo.sdp })

    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('连接超时')), 8000)
      const check = () => {
        if (token !== currentToken || stopped) { clearTimeout(timer); return }
        if (pc.connectionState === 'connected') { clearTimeout(timer); resolve() }
        else if (pc.connectionState === 'failed' || pc.connectionState === 'closed') {
          clearTimeout(timer); reject(new Error('连接失败'))
        }
      }
      pc.onconnectionstatechange = check
      check()
    })
    if (token !== currentToken || stopped) return

    statusText.value = ''
    rtcRetryCount = 0
    // 连接中断时自动重连
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed') {
        scheduleRtcRetry()
      }
    }
  } catch (e) {
    scheduleRtcRetry()
  }
}

function scheduleRtcRetry() {
  if (stopped || mode.value !== 'rtc') return
  if (rtcRetryTimer) clearTimeout(rtcRetryTimer)
  rtcRetryCount++
  if (rtcRetryCount > MAX_RTC_RETRY) {
    // WebRTC 不可用，回退 HLS
    if (props.src) {
      log('WebRTC 多次失败，切换 HLS 播放')
      startHls()
    } else {
      statusText.value = '拉流失败，请稍候重试'
    }
    return
  }
  statusText.value = `连接主播画面中...(${rtcRetryCount})`
  rtcRetryTimer = setTimeout(() => startRtc(currentToken), RTC_RETRY_INTERVAL)
}

function teardownRtc() {
  if (rtcRetryTimer) { clearTimeout(rtcRetryTimer); rtcRetryTimer = null }
  if (rtcPc) {
    try { rtcPc.close() } catch { /* 已关闭 */ }
    rtcPc = null
  }
  if (videoRef.value) videoRef.value.srcObject = null
  rtcStreamObj = null
}

// ==================== HLS 播放（兜底） ====================

async function startHls(token) {
  if (stopped || token !== currentToken) return
  teardownRtc()
  mode.value = 'hls'
  if (!videoRef.value || !props.src) return
  const { default: videojs } = await import('video.js')
  if (token !== currentToken || stopped) return
  await import('video.js/dist/video-js.css').catch(() => {})
  statusText.value = ''
  hlsPlayer = videojs(videoRef.value, {
    autoplay: true,
    muted: true,
    controls: true,
    fill: true,
    liveui: true,
    sources: [{ src: props.src, type: 'application/x-mpegURL' }]
  })
  hlsPlayer.on('error', () => {
    if (hlsRetryTimer) clearTimeout(hlsRetryTimer)
    hlsRetryCount++
    if (hlsRetryCount > MAX_HLS_RETRY) {
      statusText.value = '拉流失败，请稍候重试'
      return
    }
    statusText.value = `连接主播画面中...(${hlsRetryCount})`
    hlsRetryTimer = setTimeout(() => {
      if (stopped || token !== currentToken) return
      if (hlsPlayer) {
        try { hlsPlayer.dispose() } catch { /* 已销毁 */ }
        hlsPlayer = null
      }
      startHls(token)
    }, 3000)
  })
  hlsPlayer.on('loadeddata', () => { hlsRetryCount = 0; statusText.value = '' })
}

function teardownHls() {
  if (hlsRetryTimer) { clearTimeout(hlsRetryTimer); hlsRetryTimer = null }
  if (hlsPlayer) {
    try { hlsPlayer.dispose() } catch { /* 已销毁 */ }
    hlsPlayer = null
  }
}

// ==================== 生命周期 ====================

function start() {
  stopped = false
  currentToken++
  const token = currentToken
  rtcRetryCount = 0
  hlsRetryCount = 0
  statusText.value = ''
  if (props.rtcApi && props.rtcStream) {
    mode.value = 'rtc'
    startRtc(token)
  } else if (props.src) {
    mode.value = 'hls'
    startHls(token)
  }
}

function stop() {
  stopped = true
  currentToken++
  teardownRtc()
  teardownHls()
  statusText.value = ''
  mode.value = null
}

watch(() => [props.rtcApi, props.rtcStream, props.src], () => start())
onMounted(start)
onUnmounted(stop)
</script>

<style scoped>
.live-player-wrap {
  width: 100%;
  height: 100%;
  background: #000;
  position: relative;
}
.player-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
  display: block;
}
.player-video.vjs-style {
  width: 100%;
  height: 100%;
}
.player-status {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffb347;
  font-size: 14px;
  background: rgba(0, 0, 0, 0.55);
  pointer-events: none;
  z-index: 3;
}
</style>
