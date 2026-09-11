<template>
  <div class="room-page">
    <!-- 顶部栏 -->
    <header class="top-bar">
      <span class="back" @click="$router.back()">← 返回</span>
      <div class="anchor-info" v-if="roomInfo.roomName">
        <img :src="roomInfo.anchorImg || defaultAvatar" class="anchor-avatar" />
        <div>
          <div class="room-name">{{ roomInfo.roomName }}</div>
          <div class="anchor-name">{{ roomInfo.anchorNickName }}</div>
        </div>
      </div>
      <div class="top-actions">
        <el-button v-if="roomInfo.isAnchor" type="danger" size="small" @click="handleCloseLiving">
          结束直播
        </el-button>
      </div>
    </header>

    <!-- 直播画面区（封面占位） -->
    <div class="video-area">
      <img :src="roomInfo.defaultBgImg || defaultBg" class="room-cover" />
      <div class="cover-overlay">
        <span class="cover-tip">🎬 直播间</span>
      </div>
    </div>

    <!-- 聊天+功能区 -->
    <div class="bottom-area">
      <!-- 消息列表 -->
      <ChatList :messages="chatMessages" class="chat-section" />

      <!-- 底部操作栏 -->
      <div class="action-row">
        <ChatInput placeholder="说点什么..." @send="handleSendChat" />
        <div class="action-btns">
          <el-button size="small" @click="showGift = true">🎁 礼物</el-button>
          <el-button v-if="userStore.userInfo.showStartLivingBtn && !roomInfo.isAnchor" size="small" type="success" @click="handleStartLiving">
            开播
          </el-button>
        </div>
      </div>
    </div>

    <!-- 礼物特效动画 -->
    <Transition name="gift">
      <div v-if="giftAnimation.show" class="gift-animation">
        <img :src="giftAnimation.img" class="gift-anim-img" />
        <div class="gift-anim-text">
          <span class="gift-sender">{{ giftAnimation.sender }}</span>
          送出 <strong>{{ giftAnimation.name }}</strong>
        </div>
      </div>
    </Transition>

    <!-- PK 进度条 -->
    <div v-if="pkStatus.show" class="pk-bar">
      <div class="pk-side left">
        <span>{{ pkStatus.leftName }}</span>
        <div class="pk-progress"><div class="pk-fill" :style="{ width: pkStatus.leftPercent + '%' }"></div></div>
      </div>
      <div class="pk-center">PK</div>
      <div class="pk-side right">
        <span>{{ pkStatus.rightName }}</span>
        <div class="pk-progress"><div class="pk-fill" :style="{ width: pkStatus.rightPercent + '%' }"></div></div>
      </div>
    </div>

    <!-- 礼物选择弹窗 -->
    <GiftPanel v-model="showGift" :room-id="roomId" @send="handleSendGift" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { anchorConfig, startLiving, closeLiving, getImConfig } from '@/api/room'
import { sendGift } from '@/api/gift'
import { IMConnection } from '@/utils/im/connection'
import ChatList from '@/components/ChatList.vue'
import ChatInput from '@/components/ChatInput.vue'
import GiftPanel from '@/components/GiftPanel.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const roomId = computed(() => Number(route.params.id))
const roomInfo = ref({})
const showGift = ref(false)
const chatMessages = ref([])
const giftAnimation = reactive({ show: false, img: '', name: '', sender: '' })
const pkStatus = reactive({ show: false, leftName: '', rightName: '', leftPercent: 50, rightPercent: 50 })

const defaultAvatar = 'https://via.placeholder.com/48/667eea/fff?text=A'
const defaultBg = 'https://via.placeholder.com/750x400/1a1a2e/667eea?text=Live+Room'

let imConn = null

// 获取直播间完整信息
async function fetchRoomInfo() {
  const vo = await anchorConfig(roomId.value)
  roomInfo.value = vo.data || {}
}

// 获取IM配置并建立连接
async function initIM() {
  try {
    const cfg = await getImConfig()
    const { token, wsImServerAddress } = cfg.data || {}
    if (!token || !wsImServerAddress) return

    // 解析 wsImServerAddress (格式 host:port)
    const [host, port] = wsImServerAddress.split(':')
    const wsPort = port || 8809
    const wsUrl = `ws://${host}:${wsPort}/${token}/${userStore.userInfo.userId}/1001/${roomId.value}`

    imConn = new IMConnection({
      wsUrl,
      userId: userStore.userInfo.userId,
      appId: 20001,
      onOpen: () => console.log('[IM] 连接成功'),
      onMessage: handleIMMessage,
      onClose: () => console.log('[IM] 连接关闭'),
      onError: (e) => console.error('[IM] 错误', e)
    })

    await imConn.connect()
  } catch (e) {
    console.error('[IM] 初始化失败', e)
  }
}

// 处理IM消息
function handleIMMessage(msg) {
  try {
    // 业务消息 (code=1003)
    if (msg.code === 1003 && msg.body) {
      const body = JSON.parse(msg.body)
      const bizCode = body.bizCode

      if (bizCode === 5555) {
        // 聊天消息
        const data = JSON.parse(body.data)
        chatMessages.value.push({
          userName: data.userName || '用户',
          content: data.content,
          avatar: data.avatar || '',
          isSelf: data.userId === userStore.userInfo.userId,
          time: new Date().toLocaleTimeString()
        })
        if (chatMessages.value.length > 100) chatMessages.value.shift()
      } else if (bizCode === 5556) {
        // 送礼成功
        const data = JSON.parse(body.data)
        showGiftAnim(data)
      } else if (bizCode === 5558) {
        // PK礼物
        const data = JSON.parse(body.data)
        updatePKProgress(data)
      }
    }
  } catch (e) {
    console.error('[IM] 消息处理失败', e)
  }
}

// 礼物动画
let giftAnimTimer = null
function showGiftAnim(data) {
  if (giftAnimTimer) clearTimeout(giftAnimTimer)
  giftAnimation.img = data.giftInfo?.coverImgUrl || ''
  giftAnimation.name = data.giftInfo?.giftName || '礼物'
  giftAnimation.sender = data.senderName || '某用户'
  giftAnimation.show = true
  giftAnimTimer = setTimeout(() => { giftAnimation.show = false }, 3000)
}

// PK进度更新
function updatePKProgress(data) {
  pkStatus.show = true
  pkStatus.leftName = data.leftName || 'A队'
  pkStatus.rightName = data.rightName || 'B队'
  const total = (data.leftScore || 0) + (data.rightScore || 0)
  if (total > 0) {
    pkStatus.leftPercent = Math.round((data.leftScore / total) * 100)
    pkStatus.rightPercent = 100 - pkStatus.leftPercent
  }
}

// 发送聊天
function handleSendChat(content) {
  if (!imConn?.isConnected) {
    ElMessage.warning('未连接到聊天服务器')
    return
  }
  imConn.sendChat(content, roomId.value)
}

// 发送礼物
async function handleSendGift(gift) {
  try {
    await sendGift({
      giftId: gift.giftId,
      roomId: roomId.value,
      senderUserId: userStore.userInfo.userId,
      receiverId: roomInfo.value.anchorId,
      type: 0 // 0=普通礼物
    })
    ElMessage.success('礼物已送出')
  } catch {
    ElMessage.error('礼物发送失败')
  }
}

// 开播
async function handleStartLiving() {
  try {
    const vo = await startLiving(roomInfo.value.type || 1)
    const newRoomId = vo.data?.roomId
    if (newRoomId) router.replace(`/room/${newRoomId}`)
  } catch {
    ElMessage.error('开播失败')
  }
}

// 关播
async function handleCloseLiving() {
  try {
    await closeLiving(roomId.value)
    ElMessage.success('已结束直播')
    router.push('/')
  } catch {
    ElMessage.error('关播失败')
  }
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  await fetchRoomInfo()
  await initIM()
})

// 监听 roomId 变化（开播后 router.replace 到新房间），重新初始化
watch(() => route.params.id, async (newId) => {
  if (!newId) return
  imConn?.disconnect()
  chatMessages.value = []
  giftAnimation.show = false
  pkStatus.show = false
  roomInfo.value = {}
  await fetchRoomInfo()
  await initIM()
})

onUnmounted(() => {
  imConn?.disconnect()
})
</script>

<style scoped>
.room-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0a0a0a;
  color: #fff;
  overflow: hidden;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: #161625;
  border-bottom: 1px solid #222;
  flex-shrink: 0;
}
.back { color: #667eea; cursor: pointer; font-size: 14px; }
.anchor-info { display: flex; align-items: center; gap: 10px; flex: 1; }
.anchor-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.room-name { font-size: 15px; font-weight: bold; }
.anchor-name { font-size: 12px; color: #888; }
.top-actions { display: flex; gap: 8px; }

.video-area {
  height: 45vh;
  background: #1a1a2e;
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
}
.room-cover { width: 100%; height: 100%; object-fit: cover; display: block; }
.cover-overlay {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
}
.cover-tip { font-size: 20px; color: rgba(255,255,255,0.3); }

.bottom-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.chat-section { flex: 1; overflow: hidden; }

.action-row {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #222;
  background: #161625;
  align-items: center;
}
.action-btns { display: flex; gap: 6px; flex-shrink: 0; }

/* 礼物动画 */
.gift-animation {
  position: fixed;
  bottom: 200px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
  z-index: 100;
}
.gift-anim-img { width: 80px; height: 80px; object-fit: contain; animation: giftBounce 0.5s ease-out; }
.gift-anim-text { color: #ffd700; font-size: 16px; margin-top: 8px; text-align: center; }
.gift-sender { color: #667eea; margin-right: 4px; }
@keyframes giftBounce {
  0% { transform: scale(0.5); opacity: 0; }
  60% { transform: scale(1.1); }
  100% { transform: scale(1); opacity: 1; }
}
.gift-enter-active, .gift-leave-active { transition: opacity 0.3s; }
.gift-enter-from, .gift-leave-to { opacity: 0; }

/* PK 进度条 */
.pk-bar {
  position: fixed;
  top: 70px;
  left: 0; right: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 20px;
  background: rgba(0,0,0,0.7);
  z-index: 50;
}
.pk-side { flex: 1; font-size: 12px; }
.pk-side span { display: block; margin-bottom: 3px; }
.pk-progress { height: 6px; background: #333; border-radius: 3px; overflow: hidden; }
.pk-fill { height: 100%; transition: width 0.5s; }
.pk-side.left .pk-fill { background: #667eea; }
.pk-side.right .pk-fill { background: #f56c6c; margin-left: auto; }
.pk-center { font-size: 12px; color: #888; flex-shrink: 0; }
</style>
