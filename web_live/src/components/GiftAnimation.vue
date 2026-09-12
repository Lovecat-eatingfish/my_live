<template>
  <!-- 大礼物全屏特效（排队依次播放） -->
  <Transition name="big-gift">
    <div v-if="current" class="big-gift-layer">
      <div class="big-gift-banner">
        <span class="banner-sender">{{ current.senderName }}</span>
        送出
        <span class="banner-gift">{{ current.giftName }}</span>
      </div>
      <div :class="['big-gift-stage', current.animClass]" :key="current.key">
        <div v-if="current.isLottie" ref="lottieBox" class="lottie-box"></div>
        <img v-else :src="current.img" class="fallback-img" />
      </div>
    </div>
  </Transition>

  <!-- 小礼物漂浮特效（可多个并存） -->
  <div class="mini-gift-layer">
    <TransitionGroup name="mini-float">
      <div v-for="m in minis" :key="m.key" class="mini-gift" :style="{ left: m.left }">
        <img :src="m.img" class="mini-img" />
        <div class="mini-text">
          <span class="mini-sender">{{ m.senderName }}</span> 送 {{ m.giftName }}
        </div>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted } from 'vue'
import lottie from 'lottie-web'

const BIG_PRICE = 40        // 价格 >= 40 金币按大礼物全屏播放
const BIG_DURATION = 2800   // 大礼物全屏时长 ms
const MINI_DURATION = 1600  // 小礼物漂浮时长 ms

const current = ref(null)
const minis = ref([])
const lottieBox = ref(null)

const queue = []
let playing = false
let lottieAnim = null
let bigTimer = null
let seq = 0
const disposers = []

function matchGift(data) {
  const list = window.__qiyuGiftList || []
  return list.find(g => (data.giftId && g.giftId === data.giftId)) ||
         list.find(g => (data.url && (g.svgaUrl === data.url || g.coverImgUrl === data.url))) ||
         null
}

/**
 * 播放礼物特效（由 5556 送礼广播触发）
 * data: { url, senderId, senderName, giftId, price }
 */
function play(data) {
  const gift = matchGift(data)
  const url = data.url || gift?.svgaUrl || gift?.coverImgUrl || ''
  if (!url) return
  const price = Number(data.price ?? gift?.price ?? 0)
  const isLottie = url.endsWith('.json')
  const isBig = isLottie || price >= BIG_PRICE

  // 大礼物无 lottie 资源时按礼物类型选 CSS 兜底动画
  const animClass = url.includes('car') ? 'anim-drive' : url.includes('rocket') ? 'anim-rocket' : 'anim-zoom'

  const item = {
    key: ++seq,
    url,
    img: gift?.coverImgUrl || url,
    giftName: gift?.giftName || '神秘礼物',
    senderName: data.senderName || '神秘观众',
    isLottie,
    isBig,
    animClass
  }
  if (isBig) {
    queue.push(item)
    if (!playing) playNext()
  } else {
    minis.value.push(item)
    const key = item.key
    const t = setTimeout(() => {
      minis.value = minis.value.filter(m => m.key !== key)
    }, MINI_DURATION)
    disposers.push(() => clearTimeout(t))
  }
}

function playNext() {
  const item = queue.shift()
  if (!item) { playing = false; current.value = null; return }
  playing = true
  current.value = item
  if (item.isLottie) {
    nextTick(() => {
      if (!lottieBox.value) return
      lottieAnim?.destroy()
      lottieAnim = lottie.loadAnimation({
        container: lottieBox.value,
        path: item.url,
        renderer: 'svg',
        loop: true,
        autoplay: true
      })
    })
  }
  bigTimer = setTimeout(() => {
    lottieAnim?.destroy()
    lottieAnim = null
    playNext()
  }, BIG_DURATION)
}

onUnmounted(() => {
  if (bigTimer) clearTimeout(bigTimer)
  lottieAnim?.destroy()
  disposers.forEach(d => d())
})

defineExpose({ play })
</script>

<style scoped>
/* ===== 大礼物全屏层 ===== */
.big-gift-layer {
  position: absolute;
  inset: 0;
  z-index: 150;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  background: radial-gradient(ellipse at center, rgba(102,126,234,0.12) 0%, transparent 65%);
}
.big-gift-banner {
  display: flex; align-items: center; gap: 6px;
  background: linear-gradient(90deg, rgba(102,126,234,0.9), rgba(170,120,255,0.9));
  color: #fff;
  font-size: 18px;
  padding: 10px 26px;
  border-radius: 24px;
  box-shadow: 0 6px 24px rgba(102,126,234,0.45);
  margin-bottom: 12px;
}
.banner-sender { font-weight: bold; color: #ffe08a; }
.banner-gift { font-weight: bold; color: #ffd700; }
.big-gift-stage { width: 46vmin; height: 46vmin; }
.lottie-box, .fallback-img { width: 100%; height: 100%; }
.fallback-img { object-fit: contain; }

/* CSS 兜底动画 */
.anim-zoom { animation: giftZoom 1s ease-out; }
@keyframes giftZoom {
  0% { transform: scale(0.2) rotate(-12deg); opacity: 0; }
  55% { transform: scale(1.15) rotate(4deg); opacity: 1; }
  100% { transform: scale(1) rotate(0deg); }
}
.anim-drive { animation: giftDrive 2.6s linear; }
@keyframes giftDrive {
  0% { transform: translateX(-120%) scale(0.7); }
  12% { transform: translateX(-30%) scale(1); }
  88% { transform: translateX(30%) scale(1); }
  100% { transform: translateX(120%) scale(0.7); }
}
.anim-rocket { animation: giftRocket 2.6s ease-in-out; }
@keyframes giftRocket {
  0% { transform: translateY(60%) scale(0.5); opacity: 0; }
  25% { transform: translateY(10%) scale(1); opacity: 1; }
  100% { transform: translateY(-70%) scale(1.15); opacity: 0; }
}

.big-gift-enter-active { transition: opacity 0.25s; }
.big-gift-leave-active { transition: opacity 0.4s; }
.big-gift-enter-from, .big-gift-leave-to { opacity: 0; }

/* ===== 小礼物漂浮层 ===== */
.mini-gift-layer {
  position: absolute;
  left: 16px;
  bottom: 90px;
  z-index: 140;
  display: flex;
  flex-direction: column;
  gap: 6px;
  pointer-events: none;
}
.mini-gift {
  position: relative;
  display: flex; align-items: center; gap: 8px;
  background: rgba(0,0,0,0.55);
  border-radius: 20px;
  padding: 5px 14px 5px 6px;
  animation: miniRise 1.6s ease-out forwards;
}
@keyframes miniRise {
  0% { transform: translateY(20px); opacity: 0; }
  18% { transform: translateY(0); opacity: 1; }
  80% { transform: translateY(-24px); opacity: 1; }
  100% { transform: translateY(-40px); opacity: 0; }
}
.mini-img { width: 30px; height: 30px; object-fit: contain; }
.mini-text { font-size: 13px; color: #fff; white-space: nowrap; }
.mini-sender { color: #ffe08a; font-weight: bold; }
.mini-float-enter-active { transition: opacity 0.2s; }
.mini-float-leave-active { transition: opacity 0.3s; }
.mini-float-enter-from, .mini-float-leave-to { opacity: 0; }
</style>
