<template>
  <div class="room-page">
    <!-- 顶部栏 -->
    <header class="top-bar">
      <span class="back" @click="$router.back()">← 返回</span>
      <div class="anchor-info" v-if="roomInfo.roomName">
        <img :src="roomInfo.anchorImg || roomInfo.avatar || defaultAvatar" class="anchor-avatar" />
        <div>
          <div class="room-name">{{ roomInfo.roomName }}</div>
          <div class="anchor-name">{{ roomInfo.anchorNickName }}</div>
        </div>
      </div>
      <div class="top-actions">
        <el-button v-if="!roomInfo.anchor && roomInfo.anchorId" size="small"
          :type="isFollow ? 'info' : 'primary'" @click="toggleFollow">
          {{ isFollow ? '已关注' : '+ 关注' }}
        </el-button>
        <span class="viewer-chip share-chip" title="分享直播间" @click="handleShare">🔗 分享</span>
        <span v-if="announcement" class="viewer-chip announce-chip" :title="announcement">📢 {{ announcement }}</span>
        <span v-if="lotteryActive" class="viewer-chip lottery-chip" title="发送弹幕口令即可参与">
          🎁 抽奖中：发「{{ lotteryActive.keyword }}」
        </span>
        <span class="viewer-chip" title="在线观众">
          <span class="viewer-dot"></span>{{ viewerCount }} 人观看
        </span>
        <span class="balance-chip" @click="$router.push('/wallet')" title="去充值">
          <span class="coin-icon">🪙</span>{{ userStore.balance }}
        </span>
        <template v-if="roomInfo.anchor">
          <input v-model="linkMicUserId" class="linkmic-input" placeholder="观众ID" @keyup.enter="doInviteLinkMic" />
          <el-button size="small" @click="doInviteLinkMic">📞 邀请连麦</el-button>
          <el-button v-if="linkMicActive" size="small" type="warning" @click="doHangUp">挂断连麦</el-button>
          <el-button size="small" type="success" @click="lotteryVisible = true">🎁 抽奖</el-button>
          <el-button size="small" @click="editAnnouncement">📢 公告</el-button>
          <el-button size="small" @click="addRoomAdmin">👤 管理员</el-button>
        </template>
        <el-button v-if="!roomInfo.anchor && linkMicActive && isGuest" size="small" type="warning" @click="doHangUp">
          下麦
        </el-button>
        <el-button v-if="roomInfo.anchor" type="danger" size="small" @click="handleCloseLiving">
          结束直播
        </el-button>
      </div>
    </header>

    <!-- 三区主体：左直播画面 / 右聊天栏 -->
    <div class="main-area">
      <!-- 连麦第二画面（观众拉 HLS / 被邀请观众本地预览+推流） -->
      <div v-if="linkMicActive" class="guest-video-area">
        <div class="guest-label">🎙 连麦 · {{ guestNick }}</div>
        <video v-if="isGuest" ref="guestVideoRef" class="guest-video" autoplay muted playsinline></video>
        <LivePlayer v-else :src="guestHlsUrl" class="guest-video" />
      </div>

      <!-- 直播画面区 -->
      <div class="video-area">
      <!-- 主播端：浏览器摄像头预览 / OBS 推流地址面板 / 推流中 HLS 预览 -->
        <template v-if="roomInfo.anchor">
          <video v-show="browserPushing" ref="browserVideoRef" class="browser-preview" autoplay muted playsinline></video>
          <LivePlayer v-if="(anchorPlayUrl || anchorRtcStream) && !browserPushing"
                    :src="anchorPlayUrl" :rtc-api="anchorRtcApi" :rtc-stream="anchorRtcStream" />
          <div v-if="!anchorPlayUrl && !browserPushing" class="push-panel">
          <div class="push-panel-title">🎥 开始直播</div>
          <div class="push-actions">
            <el-button type="danger" :loading="rtcConnecting" @click="startBrowserPush">摄像头开播</el-button>
            <el-button v-if="browserPushing" type="warning" @click="stopBrowserPush">停止摄像头推流</el-button>
          </div>
          <el-divider><span class="push-divider-text">或使用 OBS 推流（更专业的画面）</span></el-divider>
          <template v-if="pushInfo.pushUrl">
            <div class="push-field">
              <span class="push-label">RTMP 地址（填入 OBS 服务器）</span>
              <div class="push-value-row">
                <span class="push-value">{{ pushInfo.pushUrl }}</span>
                <el-button size="small" type="primary" @click="copyText(pushInfo.pushUrl)">复制</el-button>
              </div>
            </div>
            <div class="push-field">
              <span class="push-label">串流密钥（可留空）</span>
              <span class="push-value">不需要，地址已包含</span>
            </div>
            <div class="push-hint">
              在 OBS「设置 → 推流」中选择自定义服务，粘贴以上地址后点击「开始推流」，画面出现即表示直播成功。
            </div>
          </template>
          <el-button v-else type="primary" plain :loading="pushLoading" @click="loadPushUrl">获取 OBS 推流地址</el-button>
        </div>
      </template>
      <!-- 观众端：HLS 播放 / 封面占位 -->
      <template v-else>
        <LivePlayer v-if="playUrl || playRtcStream" :src="playUrl" :rtc-api="playRtcApi" :rtc-stream="playRtcStream" />
        <img v-else :src="roomInfo.defaultBgImg || defaultBg" class="room-cover" />
      </template>
      <div class="cover-overlay" v-if="!playUrl && !anchorPlayUrl && !playRtcStream && !anchorRtcStream && !browserPushing">
        <span class="cover-tip">
          {{ roomInfo.anchor ? '🎬 待推流' : (streamStatus === 1 ? '🎬 直播间' : '🎬 主播暂未推流') }}
        </span>
      </div>
      <span v-if="streamStatus === 1" class="living-badge">直播中</span>
      <!-- 回放入口（观众端、未推流时） -->
      <div v-if="!roomInfo.anchor && !playUrl" class="replay-entry">
        <el-button size="small" @click="openReplayList">📼 回放列表</el-button>
      </div>
      </div>

      <!-- 右侧聊天栏 -->
      <aside class="chat-sidebar">
        <div class="chat-title">💬 互动区</div>
        <div class="contrib-panel" v-if="contribList.length">
          <div class="contrib-title">🪙 本场贡献榜</div>
          <div class="contrib-row" v-for="c in contribList" :key="c.userId">
            <span :class="['contrib-no', { top1: c.rank === 1, top2: c.rank === 2, top3: c.rank === 3 }]">{{ c.rank }}</span>
            <img :src="c.avatar || defaultAvatar" class="contrib-avatar" />
            <span class="contrib-name" @click="$router.push(`/profile/${c.userId}`)">{{ c.nickName || ('用户' + c.userId) }}</span>
            <span class="contrib-score">🪙 {{ c.score }}</span>
          </div>
        </div>
        <ChatList :messages="chatMessages" :can-mute="isAnchor || isRoomAdmin" class="chat-section" @mute="handleMuteFromChat" />
        <div class="chat-input-wrap">
          <ChatInput placeholder="说点什么..." @send="handleSendChat" />
        </div>
      </aside>
    </div>

    <!-- 底部功能条 -->
    <div class="action-bar">
      <div class="action-btns">
        <button class="action-btn gift" @click="showGift = true">
          <span class="btn-icon">🎁</span><span class="btn-label">礼物</span>
        </button>
        <button v-if="!roomInfo.anchor" class="action-btn shop" @click="shopVisible = true">
          <span class="btn-icon">🛍</span><span class="btn-label">带货</span>
        </button>
        <button v-else class="action-btn shop" @click="manageVisible = true">
          <span class="btn-icon">🛍</span><span class="btn-label">商品管理</span>
        </button>
        <button v-if="roomInfo.anchor" class="action-btn redpacket" @click="rpVisible = true">
          <span class="btn-icon">🧧</span><span class="btn-label">红包</span>
        </button>
        <button v-if="userStore.userInfo.showStartLivingBtn && !roomInfo.anchor" class="action-btn start" @click="startVisible = true">
          <span class="btn-icon">📺</span><span class="btn-label">我要开播</span>
        </button>
      </div>
    </div>

    <!-- 礼物特效动画（5556 广播驱动，大礼物全屏/小礼物漂浮） -->
    <GiftAnimation ref="giftAnimRef" />

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

    <!-- 小黄车（直播带货） -->
    <ShopPanel v-model="shopVisible" :room-id="roomId" />

    <!-- 主播商品管理（小黄车上架/下架） -->
    <ShopManageDialog v-model="manageVisible" />

    <!-- 主播发红包雨弹窗 -->
    <el-dialog v-model="rpVisible" title="🧧 发红包雨" width="380px">
      <el-form label-width="90px">
        <el-form-item label="总金额">
          <el-input-number v-model="rpForm.totalPrice" :min="1" :max="100000" />
          <span class="rp-unit">抖币</span>
        </el-form-item>
        <el-form-item label="红包个数">
          <el-input-number v-model="rpForm.totalCount" :min="1" :max="500" />
          <span class="rp-unit">个</span>
        </el-form-item>
        <div class="rp-hint">发出后全房间观众立即进入红包雨，点击红包即可领取，金额随机。</div>
      </el-form>
      <template #footer>
        <el-button @click="rpVisible = false">取消</el-button>
        <el-button type="danger" :loading="rpSending" @click="submitRedPacket">发红包雨</el-button>
      </template>
    </el-dialog>

    <!-- 红包雨特效（观众收到 5560 后触发） -->
    <RedPacketRain
      v-if="rain.redPacketId"
      :red-packet-id="rain.redPacketId"
      :room-id="roomId"
      :total-count="rain.totalCount"
      @end="rain.redPacketId = null"
    />

    <!-- 开播设置弹窗（观众视角的"我要开播"） -->
    <StartLivingDialog ref="startDialogRef" v-model="startVisible" @confirm="handleStartLiving" />

    <!-- 回放列表弹窗 -->
    <!-- 口令抽奖发起弹窗（主播） -->
    <el-dialog v-model="lotteryVisible" title="🎁 发起口令抽奖" width="380px">
      <el-form label-width="80px" size="small">
        <el-form-item label="抽奖口令">
          <el-input v-model="lotteryForm.keyword" maxlength="12" placeholder="如：旗鱼666" />
        </el-form-item>
        <el-form-item label="时长">
          <el-select v-model="lotteryForm.durationSec">
            <el-option v-for="d in lotteryDurations" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="中奖人数">
          <el-input-number v-model="lotteryForm.winnerCount" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="奖池金币">
          <el-input-number v-model="lotteryForm.rewardCoins" :min="0" :max="100000" :step="10" />
          <div class="lottery-hint">从你的余额扣除，均分给中奖者；0=纯名单</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lotteryVisible = false">取消</el-button>
        <el-button type="primary" :loading="lotterySubmitting" @click="doCreateLottery">发起抽奖</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="replayVisible" title="直播回放" width="640px">
      <div v-if="replayLoading" class="replay-empty">加载中...</div>
      <div v-else-if="!replayList.length" class="replay-empty">暂无回放记录</div>
      <div v-else class="replay-list">
        <div v-for="rec in replayList" :key="rec.id" class="replay-item">
          <ReplayPlayer :src="rec.recordUrl" />
          <span class="replay-meta">
            {{ formatTime(rec.startTime) }} · 时长 {{ formatDuration(rec.duration) }}
          </span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { anchorConfig, startLiving, closeLiving, getImConfig , onlineCount } from '@/api/room'
import { createPushUrl, getStreamStatus, getPlayUrl, getRecordList } from '@/api/stream'
import { sendGift, listGift, createRedPacket, prepareRedPacket, sendRedPacket } from '@/api/gift'
import { followUser, unfollowUser, isFollowUser } from '@/api/user'

// 直播间分享：复制带房间号的链接（落地页打开自动进房）
async function handleShare() {
  const url = `${location.origin}/room/${roomId.value}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('直播间链接已复制，快去分享吧')
  } catch {
    ElMessage.info(`直播间链接：${url}`)
  }
}
import { roomGiftRank } from '@/api/rank'
import { inviteLinkMic, acceptLinkMic, hangUpLinkMic, buyTicket, createLottery, setAnnouncement, appointRoomAdmin, muteRoomUser } from '@/api/room'
import { IMConnection } from '@/utils/im/connection'
import ChatList from '@/components/ChatList.vue'
import ChatInput from '@/components/ChatInput.vue'
import GiftPanel from '@/components/GiftPanel.vue'
import GiftAnimation from '@/components/GiftAnimation.vue'
import LivePlayer from '@/components/LivePlayer.vue'
import ReplayPlayer from '@/components/ReplayPlayer.vue'
import ShopPanel from '@/components/ShopPanel.vue'
import ShopManageDialog from '@/components/ShopManageDialog.vue'
import StartLivingDialog from '@/components/StartLivingDialog.vue'
import RedPacketRain from '@/components/RedPacketRain.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const roomId = computed(() => Number(route.params.id))
const roomInfo = ref({})
const isFollow = ref(false)
// ==================== 直播间治理（公告/管理员/禁言） ====================
const announcement = ref('')
const isRoomAdmin = ref(false)
const isAnchor = computed(() => !!roomInfo.value.anchor)

async function editAnnouncement() {
  try {
    const { value } = await ElMessageBox.prompt('观众进入直播间会看到公告', '📢 直播间公告', {
      inputValue: announcement.value, inputPattern: /^.{0,200}$/, inputErrorMessage: '最多 200 字',
      confirmButtonText: '保存', cancelButtonText: '取消'
    })
    const vo = await setAnnouncement(roomId.value, value || '')
    if (vo.data) { ElMessage.error(vo.data); return }
    announcement.value = value || ''
    ElMessage.success(announcement.value ? '公告已更新' : '公告已清除')
  } catch { /* 取消 */ }
}

async function addRoomAdmin() {
  try {
    const { value } = await ElMessageBox.prompt('输入要任命为管理员的观众用户 ID', '👤 任命房间管理员', {
      inputPattern: /^\d+$/, inputErrorMessage: '请输入数字用户 ID',
      confirmButtonText: '任命', cancelButtonText: '取消'
    })
    const vo = await appointRoomAdmin(roomId.value, Number(value))
    if (vo.data) { ElMessage.error(vo.data); return }
    roomInfo.value.roomAdmins = [...(roomInfo.value.roomAdmins || []), Number(value)]
    ElMessage.success('已任命 ' + value + ' 为房间管理员')
  } catch { /* 取消 */ }
}

async function handleMuteFromChat(msg) {
  try {
    await ElMessageBox.confirm(`禁言「${msg.userName}」30 分钟？`, '🔇 房间禁言', {
      confirmButtonText: '禁言', cancelButtonText: '取消', type: 'warning'
    })
    const vo = await muteRoomUser(roomId.value, msg.userId, 30)
    if (vo.data) { ElMessage.error(vo.data); return }
    chatMessages.value.push({ userName: '系统', content: `${msg.userName} 已被禁言 30 分钟`, system: true, time: new Date().toLocaleTimeString() })
  } catch { /* 取消 */ }
}

// ==================== 口令抽奖（5574/5575） ====================
const lotteryVisible = ref(false)
const lotteryActive = ref(null)
const lotteryForm = reactive({ keyword: '', durationSec: 60, winnerCount: 1, rewardCoins: 0 })
const lotterySubmitting = ref(false)
const lotteryDurations = [
  { label: '30 秒', value: 30 }, { label: '1 分钟', value: 60 },
  { label: '2 分钟', value: 120 }, { label: '3 分钟', value: 180 }, { label: '5 分钟', value: 300 },
]
async function doCreateLottery() {
  if (!lotteryForm.keyword.trim()) { ElMessage.warning('请设置抽奖口令'); return }
  lotterySubmitting.value = true
  try {
    const vo = await createLottery(roomId.value, lotteryForm.keyword.trim(),
      lotteryForm.durationSec, lotteryForm.winnerCount, lotteryForm.rewardCoins || 0)
    if (vo.data) { ElMessage.error(vo.data); return }
    lotteryVisible.value = false
    ElMessage.success('抽奖已发起')
  } catch (e) {
    ElMessage.error(e?.message || '发起失败')
  } finally {
    lotterySubmitting.value = false
  }
}

// ==================== 连麦（5572） ====================
const linkMicUserId = ref('')
const linkMicActive = ref(false)   // 房间当前有连麦（观众显示第二画面）
const guestHlsUrl = ref('')        // 观众拉的第二路 HLS
const guestNick = ref('')
let guestPc = null                 // 被邀请观众的第二路 RTCPeerConnection
let guestStream = null
const guestVideoRef = ref(null)
const isGuest = ref(false)         // 当前用户是否为本场连麦观众
let ticketPrompting = false        // 购票弹窗去重

async function handleLinkMicSignal(data) {
  if (data.action === 'invite') {
    if (Number(data.roomId) !== Number(roomId.value)) return
    try {
      await ElMessageBox.confirm('主播邀请你连麦，是否接受？', '连麦邀请', {
        confirmButtonText: '接受', cancelButtonText: '拒绝', type: 'info'
      })
      await acceptLinkMic(data.linkMicId)
      ElMessage.success('连麦已接受，正在接通摄像头…')
    } catch { /* 拒绝则忽略 */ }
  } else if (data.action === 'accepted') {
    // 我是连麦观众：开第二路 WebRTC 推流
    startGuestPublish(data.rtcPublishApi, data.rtcStreamUrl)
  } else if (data.action === 'start') {
    if (Number(data.roomId) !== Number(roomId.value)) return
    linkMicActive.value = true
    guestHlsUrl.value = data.hlsUrl || ''
    guestNick.value = data.guestNickName || ''
    chatMessages.value.push({ userName: '系统', content: `${guestNick.value} 已上麦`, avatar: '', system: true, isSelf: false, time: new Date().toLocaleTimeString() })
  } else if (data.action === 'stop') {
    if (Number(data.roomId) !== Number(roomId.value)) return
    stopGuestPublish()
    linkMicActive.value = false
    chatMessages.value.push({ userName: '系统', content: '连麦已结束', avatar: '', system: true, isSelf: false, time: new Date().toLocaleTimeString() })
  }
}

// 主播发起邀请
async function doInviteLinkMic() {
  const uid = Number(linkMicUserId.value)
  if (!uid) { ElMessage.warning('请输入要邀请的观众用户ID'); return }
  try {
    await inviteLinkMic(roomId.value, uid)
    ElMessage.success('连麦邀请已发送')
    linkMicUserId.value = ''
  } catch (e) {
    ElMessage.error(e?.message || '邀请失败')
  }
}

// 被邀请观众：第二路 WebRTC 推流（复用主播采集逻辑，指向 liveg_ 流）
async function startGuestPublish(rtcPublishApi, rtcStreamUrl) {
  try {
    if (!navigator.mediaDevices?.getUserMedia || !window.RTCPeerConnection) {
      ElMessage.error('当前浏览器不支持摄像头/WebRTC')
      return
    }
    guestStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    if (guestVideoRef.value) guestVideoRef.value.srcObject = guestStream
    const pc = new RTCPeerConnection()
    guestPc = pc
    guestStream.getTracks().forEach(track => pc.addTransceiver(track, { direction: 'sendonly' }))
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    const rtcApi = location.origin + rtcPublishApi
    const res = await fetch(rtcPublishApi, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ api: rtcApi, streamurl: rtcStreamUrl, sdp: offer.sdp })
    })
    const vo = await res.json()
    if (vo.code !== 0 || !vo.sdp) throw new Error(vo.msg || 'SRS WebRTC 信令失败')
    await pc.setRemoteDescription({ type: 'answer', sdp: vo.sdp })
    isGuest.value = true
    ElMessage.success('连麦推流已接通')
  } catch (e) {
    ElMessage.error('连麦推流失败：' + (e?.message || e))
    stopGuestPublish()
  }
}

function stopGuestPublish() {
  if (guestPc) { try { guestPc.close() } catch { } guestPc = null }
  if (guestStream) { guestStream.getTracks().forEach(t => t.stop()); guestStream = null }
  if (guestVideoRef.value) guestVideoRef.value.srcObject = null
  isGuest.value = false
}

// 连麦中挂断（主播或连麦观众都可点）
async function doHangUp() {
  await hangUpLinkMic(roomId.value)
}

// 本场贡献榜：初始从 Redis 拉一次，5556 到达时本地增量刷新（不轮询）
const contribList = ref([])
const contribMap = reactive({})
async function loadContribution() {
  try {
    const vo = await roomGiftRank(roomId.value)
    contribList.value = vo.data || []
    contribList.value.forEach(c => { contribMap[c.userId] = c.score })
  } catch { contribList.value = [] }
}
function bumpContribution(senderId, senderName, price) {
  const key = String(senderId)
  contribMap[key] = (Number(contribMap[key]) || 0) + (Number(price) || 0)
  const list = Object.entries(contribMap)
    .map(([uid, score]) => ({ userId: uid, score, nickName: key === String(senderId) && senderName ? senderName : undefined }))
    .sort((x, y) => y.score - x.score)
    .slice(0, 10)
    .map((item, i) => ({
      rank: i + 1, userId: item.userId, score: item.score,
      nickName: item.userId === String(senderId) && senderName ? senderName
        : (contribList.value.find(c => String(c.userId) === String(item.userId))?.nickName || '用户' + item.userId),
      avatar: contribList.value.find(c => String(c.userId) === String(item.userId))?.avatar || ''
    }))
  contribList.value = list
}

// 关注/取关当前房间主播
async function toggleFollow() {
  const anchorId = roomInfo.value.anchorId
  if (!anchorId) return
  if (isFollow.value) {
    await unfollowUser(anchorId)
    isFollow.value = false
    ElMessage.success('已取消关注')
  } else {
    await followUser(anchorId)
    isFollow.value = true
    ElMessage.success('关注成功，主播开播会第一时间通知你')
  }
}
const showGift = ref(false)
const chatMessages = ref([])
const giftAnimRef = ref(null)
const pkStatus = reactive({ show: false, leftName: '', rightName: '', leftPercent: 50, rightPercent: 50 })

const defaultAvatar = 'https://via.placeholder.com/48/667eea/fff?text=A'
const defaultBg = 'https://via.placeholder.com/750x400/1a1a2e/667eea?text=Live+Room'

let imConn = null

// ===== 视频流 =====
const pushInfo = reactive({ pushUrl: '', streamKey: '', expireTime: null, rtcPublishApi: '', rtcStreamUrl: '' })
const pushLoading = ref(false)
const streamStatus = ref(0) // 0=未开播 1=推流中 2=异常
const playUrl = ref('')       // 观众端 HLS 地址（WebRTC 失败时的兜底）
const anchorPlayUrl = ref('') // 主播端预览地址
const playRtcApi = ref('')    // 观众端 WebRTC 信令接口
const playRtcStream = ref('') // 观众端 WebRTC 流地址
const anchorRtcApi = ref('')  // 主播端预览 WebRTC 信令
const anchorRtcStream = ref('') // 主播端预览 WebRTC 流地址

// ===== 浏览器摄像头开播（WebRTC -> SRS）=====
const browserPushing = ref(false)
const rtcConnecting = ref(false)
const browserVideoRef = ref(null)
let rtcPc = null
let cameraStream = null
async function startBrowserPush() {
  if (rtcConnecting.value || browserPushing.value) return
  // 确保已有 WebRTC 推流地址
  if (!pushInfo.rtcStreamUrl) {
    await loadPushUrl()
    if (!pushInfo.rtcStreamUrl) return
  }
  if (!navigator.mediaDevices?.getUserMedia || !window.RTCPeerConnection) {
    ElMessage.error('当前浏览器不支持摄像头采集/WebRTC，请使用 Chrome/Edge 并通过 localhost 访问')
    return
  }
  rtcConnecting.value = true
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    browserVideoRef.value.srcObject = cameraStream

    const pc = new RTCPeerConnection()
    rtcPc = pc
    cameraStream.getTracks().forEach(track => pc.addTransceiver(track, { direction: 'sendonly' }))
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)

    // 与 SRS 交换 SDP（相对路径同源请求，经 vite 代理转发到 SRS HTTP API）
    const rtcApi = location.origin + pushInfo.rtcPublishApi
    const res = await fetch(pushInfo.rtcPublishApi, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        api: rtcApi,
        streamurl: pushInfo.rtcStreamUrl,
        sdp: offer.sdp
      })
    })
    const vo = await res.json()
    if (vo.code !== 0 || !vo.sdp) {
      throw new Error(vo.msg || vo.errmsg || 'SRS WebRTC 信令失败 code=' + vo.code)
    }
    await pc.setRemoteDescription({ type: 'answer', sdp: vo.sdp })

    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('WebRTC 连接超时')), 10000)
      const check = () => {
        if (pc.connectionState === 'connected') { clearTimeout(timer); resolve() }
        else if (pc.connectionState === 'failed' || pc.connectionState === 'closed') {
          clearTimeout(timer); reject(new Error('WebRTC 连接失败'))
        }
      }
      pc.onconnectionstatechange = check
      check()
    })
    browserPushing.value = true
    ElMessage.success('摄像头开播成功，观众正在观看你的直播')
  } catch (e) {
    console.error('[WebRTC] 开播失败', e)
    ElMessage.error('开播失败：' + (e.message || '无法访问摄像头'))
    stopBrowserPush()
  } finally {
    rtcConnecting.value = false
  }
}

function stopBrowserPush() {
  browserPushing.value = false
  if (rtcPc) {
    try { rtcPc.close() } catch { /* 已关闭 */ }
    rtcPc = null
  }
  if (cameraStream) {
    cameraStream.getTracks().forEach(t => t.stop())
    cameraStream = null
  }
  if (browserVideoRef.value) browserVideoRef.value.srcObject = null
}

// 主播获取推流地址
async function loadPushUrl() {
  try {
    pushLoading.value = true
    const vo = await createPushUrl(roomId.value)
    pushInfo.pushUrl = vo.data?.pushUrl || ''
    pushInfo.streamKey = vo.data?.streamKey || ''
    pushInfo.expireTime = vo.data?.expireTime || null
    pushInfo.rtcPublishApi = vo.data?.rtcPublishApi || ''
    pushInfo.rtcStreamUrl = vo.data?.rtcStreamUrl || ''
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    pushLoading.value = false
  }
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

// 观众/主播预览拉取播放地址
async function fetchPlayUrl(isAnchorPreview) {
  try {
    const vo = await getPlayUrl(roomId.value)
    if (vo.data?.isLiving && (vo.data?.hlsUrl || vo.data?.rtcStreamUrl)) {
      if (isAnchorPreview) {
        anchorPlayUrl.value = vo.data?.hlsUrl || ''
        anchorRtcApi.value = vo.data?.rtcPlayApi || ''
        anchorRtcStream.value = vo.data?.rtcStreamUrl || ''
      } else {
        playUrl.value = vo.data?.hlsUrl || ''
        playRtcApi.value = vo.data?.rtcPlayApi || ''
        playRtcStream.value = vo.data?.rtcStreamUrl || ''
      }
      return true
    }
  } catch {
    // 忽略，下轮重试
  }
  return false
}

// 流状态同步：不做定时轮询。房间关闭有 5565 IM 推送（自动踢回首页），
// 推流开始/停止有 5563 IM 推送触发本函数；主播开播地址在进入房间时生成一次
async function refreshStreamStatus() {
  try {
    const vo = await getStreamStatus(roomId.value)
    streamStatus.value = vo.data?.status || 0
    const isAnchor = !!roomInfo.value.anchor
    if (streamStatus.value === 1) {
      if (isAnchor && !anchorPlayUrl.value) await fetchPlayUrl(true)
      if (!isAnchor && !playUrl.value) await fetchPlayUrl(false)
    } else if (streamStatus.value === 0) {
      anchorPlayUrl.value = ''
      playUrl.value = ''
      anchorRtcStream.value = ''
      playRtcStream.value = ''
    }
  } catch {
    // 推流服务不可用时静默，不影响房间内其他功能
  }
}

function startStreamLoop() {
  // 主播进入房间自动生成推流地址
  if (roomInfo.value.anchor) loadPushUrl()
  // 进入房间时查询一次当前流状态（推流中则展示画面），后续状态变化全部依赖 5563/5565 推送
  refreshStreamStatus()
  // @DEBUG 暴露流状态，浏览器控制台用 __sd() 查看
  window.__sd = () => ({
    roomId: roomId.value,
    anchor: !!roomInfo.value.anchor,
    status: streamStatus.value,
    playUrl: playUrl.value,
    anchorPlayUrl: anchorPlayUrl.value,
    browserPushing: browserPushing.value,
    playRtcApi: playRtcApi.value,
    playRtcStream: playRtcStream.value
  })
}

function stopStreamLoop() {
  streamStatus.value = 0
  playUrl.value = ''
  anchorPlayUrl.value = ''
}

// ===== 回放列表 =====
const replayVisible = ref(false)
const replayLoading = ref(false)
const replayList = ref([])

async function openReplayList() {
  replayVisible.value = true
  replayLoading.value = true
  try {
    const vo = await getRecordList(roomId.value)
    replayList.value = vo.data || []
  } catch {
    replayList.value = []
  } finally {
    replayLoading.value = false
  }
}

function formatTime(ts) {
  if (!ts) return ''
  return new Date(Number(ts)).toLocaleString()
}

function formatDuration(seconds) {
  const s = Number(seconds) || 0
  const m = Math.floor(s / 60)
  const r = s % 60
  return m > 0 ? `${m}分${r}秒` : `${r}秒`
}

// ===== 直播带货 + 红包雨 =====
const shopVisible = ref(false)
const manageVisible = ref(false)
const rpVisible = ref(false)
const rpSending = ref(false)
const rpForm = reactive({ totalPrice: 100, totalCount: 10 })
const rain = reactive({ redPacketId: null, totalCount: 30 })

// 主播发红包雨：创建 -> 预热 -> 发送（后端 MQ -> 5560 广播全房间）
async function submitRedPacket() {
  if (rpSending.value) return
  rpSending.value = true
  try {
    const vo = await createRedPacket({
      roomId: roomId.value,
      totalPrice: rpForm.totalPrice,
      totalCount: rpForm.totalCount,
      maxGetPrice: Math.ceil((rpForm.totalPrice / rpForm.totalCount) * 2)
    })
    const redPacketId = vo.data?.redPacketId
    if (!redPacketId) throw new Error('红包创建失败')
    await prepareRedPacket({ redPacketId })
    await sendRedPacket({ redPacketId })
    ElMessage.success('红包雨已发出！')
    rpVisible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '红包发送失败')
  } finally {
    rpSending.value = false
  }
}

// 获取直播间完整信息
async function fetchRoomInfo() {
  const vo = await anchorConfig(roomId.value)
  if (vo.code === 10114) {
    // 付费直播间未购票：弹购票窗（重复弹窗去重）
    if (!ticketPrompting) {
      ticketPrompting = true
      try {
        await ElMessageBox.confirm(
          `本直播间为付费直播间，购买门票后即可观看。是否立即购票？`,
          '付费直播间', { confirmButtonText: '购票进入', cancelButtonText: '离开', type: 'warning' }
        ).then(async () => {
          await buyTicket(roomId.value)
          ElMessage.success('购票成功，欢迎观看')
          ticketPrompting = false
          return fetchRoomInfo()
        }).catch(() => {
          ticketPrompting = false
          router.back()
        })
      } catch (e) {
        ticketPrompting = false
        ElMessage.error(e?.message || '购票失败')
      }
    }
    return
  }
  roomInfo.value = vo.data || {}
  announcement.value = vo.data?.announcement || ''
  isRoomAdmin.value = !!vo.data?.isRoomAdmin
  if (!roomInfo.value.anchor && roomInfo.value.anchorId) {
    const f = await isFollowUser(roomInfo.value.anchorId)
    isFollow.value = !!f.data
  }
  loadContribution()
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
      appId: 10001,
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
const seenMsgIds = new Set()
function handleIMMessage(msg) {
  try {
    // 业务消息 (code=1003)，msgId 在 body（ImMsgBody JSON）内部
    if (msg.code === 1003 && msg.body) {
      const body = JSON.parse(msg.body)
      // ack 丢失时服务端会延迟重推，按 body.msgId 去重兜底
      if (body.msgId) {
        if (seenMsgIds.has(body.msgId)) return
        seenMsgIds.add(body.msgId)
        if (seenMsgIds.size > 500) seenMsgIds.delete(seenMsgIds.values().next().value)
      }
      const bizCode = body.bizCode

      if (bizCode === 5555) {
        // 聊天消息（data结构对齐后端MessageDTO：senderName/senderAvtar/content）
        const data = JSON.parse(body.data)
        chatMessages.value.push({
          userName: data.senderName || '用户',
          userId: data.userId,
          level: data.level,
          content: data.content,
          avatar: data.senderAvtar || '',
          system: !!data.system,
          isSelf: data.userId === userStore.userInfo.userId,
          time: new Date().toLocaleTimeString()
        })
        if (chatMessages.value.length > 100) chatMessages.value.shift()
      } else if (bizCode === 5556) {
        // 送礼成功：播放礼物特效；送礼人自己顺带刷新余额（扣费后）
        const data = JSON.parse(body.data)
        giftAnimRef.value?.play(data)
        bumpContribution(data.senderId, data.senderName, data.price)
        if (Number(data.senderId) === Number(userStore.userInfo.userId)) {
          userStore.refreshBalance()
        }
      } else if (bizCode === 5557) {
        // 送礼失败（余额不足等，后端MQ消费者异步扣费失败后单独推送）
        const data = JSON.parse(body.data)
        const failMsg = data.msg || '送礼失败'
        if (/余额不足/.test(failMsg)) {
          ElMessageBox.confirm('金币余额不足，无法送出礼物，是否前往充值？', '余额不足', {
            confirmButtonText: '去充值',
            cancelButtonText: '取消',
            type: 'warning'
          }).then(() => router.push('/wallet')).catch(() => {})
        } else {
          ElMessage.error(failMsg)
        }
      } else if (bizCode === 5566) {
        // 风控提示（禁言/敏感词拦截，后端单发给发送者本人）
        const data = JSON.parse(body.data)
        ElMessage.warning(data.content || '消息包含敏感内容，已被拦截')
      } else if (bizCode === 5572) {
        // 连麦信令：invite 单发被邀请人 / accepted 单发观众(含推流参数) / start+stop 全房间
        const data = JSON.parse(body.data)
        handleLinkMicSignal(data)
      } else if (bizCode === 5574) {
        // 口令抽奖开始：主播端发起，观众发对应口令弹幕即参与
        const data = JSON.parse(body.data)
        lotteryActive.value = {
          keyword: data.keyword, endTime: data.endTime,
          winnerCount: data.winnerCount, rewardCoins: data.rewardCoins
        }
        ElMessage.success(`🎁 抽奖开始！发送弹幕「${data.keyword}」参与${data.rewardCoins > 0 ? `，奖池 ${data.rewardCoins} 金币` : ''}`)
      } else if (bizCode === 5575) {
        // 口令抽奖开奖
        const data = JSON.parse(body.data)
        lotteryActive.value = null
        const me = Number(userStore.userInfo.userId)
        const winners = data.winners || []
        const iWin = winners.some(w => Number(w) === me)
        const rewardTxt = data.rewardPerPerson > 0 ? `，每人 ${data.rewardPerPerson} 金币` : ''
        const who = winners.map(w => '用户' + w).join('、') || '无人参与，奖励退回'
        const msg = `🎁 抽奖开奖：${who}${rewardTxt}`
        if (iWin) ElMessage({ message: '🎉 恭喜你中奖！' + msg, type: 'success', duration: 10000 })
        else ElMessage({ message: msg, duration: 8000 })
        chatMessages.value.push({ userName: '系统', content: `🎁 抽奖开奖（口令 ${data.keyword}）：${who}${rewardTxt}`, system: true, time: new Date().toLocaleTimeString() })
      } else if (bizCode === 5567) {
        // 关注的主播开播推送（点 Toast 跳转房间）
        const data = JSON.parse(body.data)
        ElMessage({
          message: `📣 你关注的主播 ${data.anchorName || ''} 开播啦${data.roomName ? '：' + data.roomName : ''}（点击前往）`,
          type: 'success',
          duration: 8000,
          onClick: () => { if (data.roomId) router.push(`/room/${data.roomId}`) }
        })
      } else if (bizCode === 5570) {
        // 升级特效：本人弹 Toast，其他人以系统消息入聊天流
        const data = JSON.parse(body.data)
        if (Number(data.userId) === Number(userStore.userInfo.userId)) {
          ElMessage.success(`🎉 恭喜！你升级到了 L${data.level}`)
        } else {
          chatMessages.value.push({
            userName: '系统',
            content: `${data.nickName || '有人'} 升级到了 L${data.level} 🎉`,
            avatar: '',
            isSelf: false,
            time: new Date().toLocaleTimeString()
          })
          if (chatMessages.value.length > 100) chatMessages.value.shift()
        }
      } else if (bizCode === 5558) {
        // PK礼物
        const data = JSON.parse(body.data)
        updatePKProgress(data)
      } else if (bizCode === 5560) {
        // 红包雨来了
        const data = JSON.parse(body.data)
        if (data.roomId && Number(data.roomId) !== roomId.value) return
        rain.redPacketId = data.redPacketId
        rain.totalCount = Number(data.totalCount) || 30
      } else if (bizCode === 5561) {
        // 红包领取成功（后端单独推给领取人）
        ElMessage.success('🧧 红包领取成功！')
        userStore.refreshBalance()
      } else if (bizCode === 5563) {
        // 推流状态变更（后端 IM 广播，前端无需再等轮询）
        const data = JSON.parse(body.data)
        if (data.roomId && Number(data.roomId) !== roomId.value) return
        streamStatus.value = data.status
        if (Number(data.status) === 1) {
          refreshStreamStatus()
        } else {
          anchorPlayUrl.value = ''
          playUrl.value = ''
          ElMessage.info('主播已停止推流')
        }
      } else if (bizCode === 5564) {
        // 回放生成通知
        const data = JSON.parse(body.data)
        if (data.roomId && Number(data.roomId) !== roomId.value) return
        ElMessage.success('本场直播回放已生成，可在回放列表查看')
      } else if (bizCode === 5565) {
        // 直播间已关闭（主播主动关播或主播离开/断线）
        const closedRoomId = Number(JSON.parse(body.data))
        if (!Number.isNaN(closedRoomId) && closedRoomId !== roomId.value) return
        ElMessage.info('主播已离开，直播间已关闭')
        router.push('/')
      }
    }
  } catch (e) {
    console.error('[IM] 消息处理失败', e)
  }
}

// 礼物动画：由 GiftAnimation 组件负责（play 由 5556 处理器调用）

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
  imConn.sendChat(content, roomId.value, {
    senderName: userStore.userInfo.nickName,
    senderAvtar: userStore.userInfo.avatar
  })
  // 后端不会把消息回推给发送者自己，本地直接回显
  chatMessages.value.push({
    userName: userStore.userInfo.nickName || '我',
    content,
    avatar: userStore.userInfo.avatar || '',
    isSelf: true,
    time: new Date().toLocaleTimeString()
  })
  if (chatMessages.value.length > 100) chatMessages.value.shift()
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

// 开播：由 StartLivingDialog 收集名称与封面后回调
const startVisible = ref(false)
const startDialogRef = ref(null)
async function handleStartLiving({ roomName, covertImg, payType, ticketPrice }) {
  try {
    const vo = await startLiving(roomInfo.value.type || 1, roomName, covertImg, payType, ticketPrice)
    const newRoomId = vo.data?.roomId
    if (newRoomId) {
      startDialogRef.value?.finish()
      router.replace(`/room/${newRoomId}`)
    } else {
      ElMessage.error('开播失败')
      startDialogRef.value?.fail()
    }
  } catch {
    ElMessage.error('开播失败')
    startDialogRef.value?.fail()
  }
}

// 关播
async function handleCloseLiving() {
  try {
    await closeLiving(roomId.value)
    stopBrowserPush()
    ElMessage.success('已结束直播')
    router.push('/')
  } catch {
    ElMessage.error('关播失败')
  }
}

// 在线观众数：10 秒轮询刷新
const viewerCount = ref(0)
let viewerTimer = null
async function refreshViewerCount() {
  try {
    const vo = await onlineCount(roomId.value)
    viewerCount.value = Number(vo.data) || 0
  } catch { /* 静默，下一轮再刷 */ }
}

onMounted(async () => {
  // 各初始化步骤独立容错：任何一步失败都不能阻断视频流轮询启动
  try {
    await userStore.fetchUserInfo()
    await fetchRoomInfo()
  } catch (e) {
    window.__mountError = (e && (e.msg || e.message)) || String(e)
    console.error('[RoomPage] 初始化失败', e)
  }
  refreshViewerCount()
  viewerTimer = setInterval(refreshViewerCount, 10000)
  userStore.refreshBalance()
  // 进房即拉礼物列表（GiftAnimation 按 giftId/url 匹配礼物信息用）
  listGift().then(vo => { window.__qiyuGiftList = vo.data || [] }).catch(() => {})
  // 视频流轮询必须最先启动，不能被 IM 连接阻塞（IM 挂起会导致观众永远看不到画面）
  startStreamLoop()
  // IM 异步连接，失败/挂起只影响弹幕不影响视频
  initIM().catch(e => console.error('[RoomPage] IM 初始化失败', e))
})

// 监听 roomId 变化（开播后 router.replace 到新房间），重新初始化
watch(() => route.params.id, async (newId) => {
  if (!newId) return
  imConn?.disconnect()
  chatMessages.value = []
  pkStatus.show = false
  roomInfo.value = {}
  stopStreamLoop()
  stopBrowserPush()
  pushInfo.pushUrl = ''
  pushInfo.streamKey = ''
  await fetchRoomInfo()
  await initIM()
  startStreamLoop()
})

onUnmounted(() => {
  imConn?.disconnect()
  stopStreamLoop()
  stopBrowserPush()
  if (viewerTimer) clearInterval(viewerTimer)
})
</script>

<style scoped>
.room-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--sq-abyss);
  color: #fff;
  overflow: hidden;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: var(--sq-deep);
  border-bottom: 1px solid var(--sq-line);
  flex-shrink: 0;
}
.back { color: var(--sq-blue); cursor: pointer; font-size: 14px; }
.anchor-info { display: flex; align-items: center; gap: 10px; flex: 1; }
.anchor-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.room-name { font-size: 15px; font-weight: bold; }
.anchor-name { font-size: 12px; color: #888; }
.top-actions { display: flex; gap: 10px; align-items: center; }
.balance-chip { font-family: var(--sq-font-mono);
  display: inline-flex; align-items: center; gap: 4px;
  background: linear-gradient(135deg, #3a2c00, #4a3a00);
  border: 1px solid #7a5c00; color: #ffd700;
  font-size: 13px; font-weight: bold;
  padding: 4px 12px; border-radius: 16px; cursor: pointer;
  transition: all 0.2s;
}
.balance-chip:hover { border-color: #ffd700; }
.viewer-chip {
  display: inline-flex; align-items: center; gap: 6px;
  background: rgba(102, 126, 234, 0.15);
  border: 1px solid rgba(102, 126, 234, 0.4); color: #aab4ff;
  font-size: 13px; padding: 4px 12px; border-radius: 16px;
}
.viewer-dot {
  width: 14px; height: 8px; position: relative;
}
/* 签名元素：鱼群游动——三个小鱼点依次穿行 */
.viewer-dot::before, .viewer-dot::after {
  content: ''; position: absolute; top: 50%; width: 5px; height: 5px;
  border-radius: 50% 50% 50% 0; /* 鱼形：尾巴收尖 */
  background: var(--sq-blue);
  animation: fish-swim 2.6s linear infinite;
}
.viewer-dot::before { animation-delay: 0s; }
.viewer-dot::after { width: 4px; height: 4px; opacity: 0.55; animation-delay: 0.5s; }
@keyframes fish-swim {
  0% { left: -4px; opacity: 0; transform: translateY(0); }
  15% { opacity: 1; }
  50% { transform: translateY(-2px); }
  85% { opacity: 1; }
  100% { left: 12px; opacity: 0; transform: translateY(1px); }
}
@media (prefers-reduced-motion: reduce) {
  .viewer-dot::before, .viewer-dot::after { animation: none; opacity: 1; left: 3px; }
}
.coin-icon { font-size: 13px; }

/* 三区主体：左视频 / 右聊天 */
.main-area {
  flex: 1;
  display: flex;
  min-height: 0;
}
.video-area {
  flex: 1;
  background: #000;
  position: relative;
  min-width: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.video-area :deep(video),
.video-area :deep(.flv-player),
.video-area .browser-preview { max-width: 100%; max-height: 100%; }
.chat-sidebar {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: rgba(22, 22, 37, 0.92);
  border-left: 1px solid var(--sq-line);
  min-height: 0;
}
.chat-title {
  padding: 10px 14px;
  font-size: 13px;
  color: #8a8aa0;
  border-bottom: 1px solid var(--sq-line);
  flex-shrink: 0;
}
.chat-section { flex: 1; overflow: hidden; min-height: 0; }
.chat-input-wrap { padding: 10px; border-top: 1px solid var(--sq-line); flex-shrink: 0; }
.chat-input-wrap :deep(.chat-input-row) { border-top: none; background: transparent; padding: 0; }

/* 底部功能条 */
.action-bar {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  background: var(--sq-deep);
  border-top: 1px solid var(--sq-line);
  flex-shrink: 0;
}
.action-btns { display: flex; gap: 14px; }
.action-btn {
  display: flex; align-items: center; gap: 6px;
  background: var(--sq-card);
  border: 1px solid #2c2c44;
  color: #ddd;
  font-size: 13px;
  padding: 7px 18px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;
}
.action-btn:hover { border-color: var(--sq-blue); color: #fff; }
.action-btn.gift:hover { border-color: #ff7a45; }
.action-btn.redpacket:hover { border-color: #f5222d; }
.action-btn.start { border-color: #2ba471; color: #6ee7b7; }
.btn-icon { font-size: 15px; }
.btn-label { font-size: 13px; }

.room-cover { width: 100%; height: 100%; object-fit: cover; display: block; }
.cover-overlay {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  pointer-events: none;
}
.cover-tip { font-size: 20px; color: rgba(255,255,255,0.3); }
.living-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  background: #f5222d;
  color: #fff;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  z-index: 5;
}

/* 主播端推流面板 */
.push-panel {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 14px;
  padding: 20px 32px;
  background: rgba(10, 10, 20, 0.85);
  overflow: auto;
}
.push-panel-title { font-size: 18px; font-weight: bold; }
.push-actions { display: flex; gap: 10px; align-items: center; }
.push-divider-text { font-size: 12px; color: #8a8aa0; }
.browser-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #000;
  display: block;
}
.push-field { display: flex; flex-direction: column; gap: 4px; }
.push-label { font-size: 12px; color: #8a8aa0; }
.push-value-row { display: flex; align-items: center; gap: 10px; }
.push-value {
  font-family: monospace;
  font-size: 13px;
  color: #66eea0;
  word-break: break-all;
  flex: 1;
}
.push-hint { font-size: 12px; color: #8a8aa0; line-height: 1.7; }

/* 回放入口 + 回放弹窗 */
.replay-entry {
  position: absolute;
  bottom: 12px;
  right: 12px;
  z-index: 5;
}
.replay-empty {
  text-align: center;
  color: #888;
  padding: 32px 0;
}
.replay-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 60vh;
  overflow: auto;
}
.replay-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.replay-video {
  width: 100%;
  border-radius: 8px;
  background: #000;
}
.replay-meta {
  font-size: 12px;
  color: #888;
}
.rp-unit {
  margin-left: 8px;
  color: #888;
  font-size: 13px;
}
.rp-hint {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
  padding: 0 4px;
}

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
.pk-progress { height: 6px; background: var(--sq-line); border-radius: 3px; overflow: hidden; }
.pk-fill { height: 100%; transition: width 0.5s; }
.pk-side.left .pk-fill { background: var(--sq-blue); }
.pk-side.right .pk-fill { background: #f56c6c; margin-left: auto; }
.pk-center { font-size: 12px; color: #888; flex-shrink: 0; }

/* 本场贡献榜 */
.contrib-panel {
  padding: 8px 10px; border-bottom: 1px solid var(--sq-line, #222);
  max-height: 180px; overflow-y: auto;
}
.contrib-title { font-size: 12px; color: #888; margin-bottom: 6px; }
.contrib-row { display: flex; align-items: center; gap: 8px; padding: 3px 0; }
.contrib-no { width: 18px; text-align: center; font-size: 12px; font-weight: bold; color: #666; }
.contrib-no.top1 { color: #ffd700; }
.contrib-no.top2 { color: #c0c0c0; }
.contrib-no.top3 { color: #cd7f32; }
.contrib-avatar { width: 22px; height: 22px; border-radius: 50%; object-fit: cover; }
.contrib-name { flex: 1; font-size: 12px; color: #ccc; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.contrib-name:hover { color: #667eea; }
.contrib-score { font-size: 12px; color: #e6a23c; }

/* 连麦 */
.linkmic-input {
  width: 90px; padding: 4px 8px; border-radius: 6px; border: 1px solid #2a3040;
  background: rgba(255,255,255,0.06); color: #fff; outline: none; font-size: 12px;
}
.guest-video-area {
  position: relative; width: 240px; flex-shrink: 0;
  border-left: 1px solid #222; background: #000; display: flex; flex-direction: column;
}
.guest-label { padding: 6px 10px; font-size: 12px; color: #aaa; }
.guest-video { width: 100%; flex: 1; object-fit: contain; background: #000; }
.announce-chip { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; background: rgba(64, 158, 255, 0.15); border: 1px solid rgba(64, 158, 255, 0.5); }
.lottery-chip { background: rgba(103, 194, 58, 0.15); border: 1px solid rgba(103, 194, 58, 0.5); }
.lottery-hint { font-size: 11px; color: #999; margin-top: 4px; }
</style>