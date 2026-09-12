<template>
  <div class="rain-overlay" @click.self="noop">
    <!-- 下落的红包 -->
    <div
      v-for="p in packets"
      :key="p.id"
      class="rain-packet"
      :class="{ grabbed: p.grabbed }"
      :style="{ left: p.left, animationDuration: p.duration, animationDelay: p.delay, fontSize: p.size }"
      :data-id="p.id"
      @click="grab(p)"
    >
      🧧
    </div>

    <!-- 顶部提示 -->
    <div class="rain-tip">🧧 红包雨来啦！点击红包领取</div>

    <!-- 领取结果 -->
    <div v-if="result" class="rain-result">
      <template v-if="result.amount > 0">
        <div class="rain-result-amount">{{ result.amount }} 抖币</div>
        <div class="rain-result-text">恭喜你抢到红包！</div>
      </template>
      <template v-else>
        <div class="rain-result-text">手慢了，红包被抢完啦~</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { receiveRedPacket } from '@/api/gift'

const props = defineProps({
  redPacketId: { type: Number, required: true },
  roomId: { type: Number, required: true },
  totalCount: { type: Number, default: 30 }
})
const emit = defineEmits(['end', 'grabbed'])

const RAIN_DURATION = 10000

const packets = ref([])
const result = ref(null)
let grabbed = false
let endTimer = null

function buildPackets() {
  const count = Math.min(Math.max(props.totalCount, 10), 50)
  const list = []
  for (let i = 0; i < count; i++) {
    list.push({
      id: i,
      left: (Math.random() * 92).toFixed(2) + '%',
      duration: (2.6 + Math.random() * 2.4).toFixed(2) + 's',
      delay: (Math.random() * (RAIN_DURATION / 1000 - 4)).toFixed(2) + 's',
      size: 26 + Math.round(Math.random() * 16) + 'px',
      grabbed: false
    })
  }
  packets.value = list
}

// 抢红包：整场只允许真正领取一次，其余点击提示
async function grab(p) {
  if (p.grabbed) return
  p.grabbed = true
  if (grabbed) return
  grabbed = true
  try {
    const vo = await receiveRedPacket({ redPacketId: props.redPacketId, roomId: props.roomId })
    const amount = Number(vo.data) || 0
    result.value = { amount }
    emit('grabbed', amount)
  } catch {
    result.value = { amount: 0 }
  }
  finishSoon()
}

function finishSoon() {
  if (endTimer) clearTimeout(endTimer)
  endTimer = setTimeout(finish, 1800)
}

function finish() {
  if (endTimer) clearTimeout(endTimer)
  endTimer = null
  emit('end')
}

function noop() {}

onMounted(() => {
  buildPackets()
  endTimer = setTimeout(finish, RAIN_DURATION + 2500)
})

onUnmounted(() => {
  if (endTimer) clearTimeout(endTimer)
})
</script>

<style scoped>
.rain-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.25);
}
.rain-packet {
  position: absolute;
  top: -60px;
  cursor: pointer;
  user-select: none;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.35));
  animation-name: rain-fall;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
  transition: transform 0.15s;
}
.rain-packet:hover {
  transform: scale(1.25);
}
.rain-packet.grabbed {
  opacity: 0.25;
  pointer-events: none;
}
@keyframes rain-fall {
  0% {
    transform: translateY(-10vh) rotate(-12deg);
  }
  100% {
    transform: translateY(112vh) rotate(16deg);
  }
}
.rain-tip {
  position: absolute;
  top: 14%;
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(90deg, #ff4d4f, #ff7a45);
  color: #fff;
  font-size: 15px;
  padding: 8px 22px;
  border-radius: 20px;
  box-shadow: 0 4px 16px rgba(255, 77, 79, 0.5);
}
.rain-result {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: linear-gradient(160deg, #ff6034, #ee0a24);
  color: #fff;
  border-radius: 16px;
  padding: 30px 46px;
  text-align: center;
  box-shadow: 0 12px 40px rgba(238, 10, 36, 0.55);
}
.rain-result-amount {
  font-size: 38px;
  font-weight: bold;
  margin-bottom: 8px;
}
.rain-result-text {
  font-size: 15px;
  opacity: 0.95;
}
</style>
