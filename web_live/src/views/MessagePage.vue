<template>
  <div class="dm-page">
    <!-- 左侧会话列表 -->
    <aside class="conv-list">
      <div class="conv-head">💬 私信</div>
      <div v-if="!conversations.length" class="conv-empty">暂无会话，去直播间认识新朋友吧</div>
      <div
        v-for="c in conversations"
        :key="c.peerUid"
        :class="['conv-item', { active: currentPeer === c.peerUid }]"
        @click="openConversation(c.peerUid)"
      >
        <img :src="c.peerAvatar || defaultAvatar" class="conv-avatar" />
        <div class="conv-main">
          <div class="conv-row">
            <span class="conv-nick">{{ c.peerNick }}</span>
            <span class="conv-time">{{ fmtTime(c.updateTime) }}</span>
          </div>
          <div class="conv-row">
            <span class="conv-last">{{ c.lastMsg }}</span>
            <span v-if="c.unreadCnt > 0" class="conv-unread">{{ c.unreadCnt > 99 ? '99+' : c.unreadCnt }}</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧聊天窗口 -->
    <section class="chat-panel">
      <template v-if="currentPeer">
        <div class="chat-head">与 {{ currentNick }} 的对话</div>
        <div class="chat-msgs" ref="msgsBox">
          <div
            v-for="m in messages"
            :key="m.msgId"
            :class="['dm-msg', { self: m.fromUid === myUserId }]"
          >
            <div class="dm-content">{{ m.content }}</div>
            <div class="dm-time">{{ fmtTime(m.createTime) }}</div>
          </div>
        </div>
        <div class="chat-input-row">
          <el-input
            v-model="draft"
            maxlength="500"
            placeholder="输入消息，回车发送"
            @keydown.enter="sendMsg"
          />
          <el-button type="primary" :disabled="!draft.trim() || !connReady" @click="sendMsg">发送</el-button>
        </div>
      </template>
      <div v-else class="chat-placeholder">选择一个会话开始聊天</div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { IMConnection } from '@/utils/im/connection'
import { getImConfig } from '@/api/room'
import { dmConversations, dmHistory, dmMarkRead } from '@/api/dm'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
let myUserId = userStore.userInfo?.userId

const conversations = ref([])
const currentPeer = ref(null)
const currentNick = ref('')
const messages = ref([])
const draft = ref('')
const msgsBox = ref(null)
const connReady = ref(false)
const defaultAvatar = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" rx="20" fill="%23ddd"/></svg>'

let imConn = null
const seenMsgIds = new Set()
const convMap = new Map()

async function loadConversations() {
  const vo = await dmConversations()
  conversations.value = vo.data || []
  convMap.clear()
  conversations.value.forEach((c) => convMap.set(c.peerUid, c))
}

async function openConversation(peerUid) {
  const conv = convMap.get(peerUid)
  currentPeer.value = peerUid
  currentNick.value = conv?.peerNick || '用户' + peerUid
  const vo = await dmHistory(peerUid, 50)
  messages.value = vo.data || []
  if (conv && conv.unreadCnt > 0) {
    conv.unreadCnt = 0
    dmMarkRead(peerUid).catch(() => {})
  }
  scrollBottom()
}

function handleDown(data) {
  if (seenMsgIds.has(data.msgId)) return
  seenMsgIds.add(data.msgId)
  const peer = data.fromUid === myUserId ? data.toUid : data.fromUid
  const isSelf = data.fromUid === myUserId
  if (peer === currentPeer.value) {
    messages.value.push(data)
    scrollBottom()
    if (!isSelf) dmMarkRead(peer).catch(() => {})
  }
  const conv = convMap.get(peer)
  if (conv) {
    conv.lastMsg = data.content
    conv.updateTime = data.createTime
    if (!isSelf && peer !== currentPeer.value) conv.unreadCnt = (conv.unreadCnt || 0) + 1
    conversations.value.sort((a, b) => new Date(b.updateTime) - new Date(a.updateTime))
  } else {
    //新会话（首次往来）：全量刷新拿昵称头像
    loadConversations().then(() => {
      if (peer === currentPeer.value) {
        const c = convMap.get(peer)
        if (c) currentNick.value = c.peerNick
      }
    })
  }
}

function handleIMMessage(msg) {
  if (msg.code !== 1003 || !msg.body) return
  try {
    const body = JSON.parse(msg.body)
    if (body.bizCode !== 5569) return
    const data = JSON.parse(body.data)
    handleDown(data)
  } catch (e) {
    /* 非 DM 消息忽略 */
  }
}

async function sendMsg() {
  const content = draft.value.trim()
  if (!content) return
  if (!imConn || !connReady.value) {
    ElMessage.warning('消息通道连接中，请稍候重试')
    return
  }
  imConn.sendDm(currentPeer.value, content)
  draft.value = ''
}

function scrollBottom() {
  nextTick(() => {
    if (msgsBox.value) msgsBox.value.scrollTop = msgsBox.value.scrollHeight
  })
}

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const today = new Date().toDateString() === d.toDateString()
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return today ? hm : `${d.getMonth() + 1}/${d.getDate()} ${hm}`
}

let reconnectTimer = null
async function initIM() {
  try {
    const cfg = await getImConfig()
    const { token, wsImServerAddress } = cfg.data || {}
    if (!token || !wsImServerAddress) return
    const [host, port] = wsImServerAddress.split(':')
    //roomId 用 0：私信不属于任何直播间，仅借登录包完成 uid→ip 绑定
    const wsUrl = `ws://${host}:${port || 8809}/${token}/${myUserId}/1001/0`
    imConn = new IMConnection({
      wsUrl,
      userId: myUserId,
      appId: 10001,
      onOpen: () => { connReady.value = true },
      onMessage: handleIMMessage,
      onClose: () => {
        connReady.value = false
        reconnectTimer = setTimeout(initIM, 3000)
      },
      onError: () => {}
    })
    await imConn.connect()
  } catch (e) {
    console.error('[DM] IM 初始化失败', e)
  }
}

onMounted(async () => {
  // 直链进入本页时 userStore 尚未拉取用户信息，userId 为 null 会导致 IM 登录失败
  if (!myUserId) {
    await userStore.fetchUserInfo()
    myUserId = userStore.userInfo?.userId
  }
  await loadConversations()
  await initIM()
  //支持 /messages?peer=xxx 直达某会话
  const peer = Number(route.query.peer)
  if (peer && convMap.has(peer)) openConversation(peer)
})

onUnmounted(() => {
  clearTimeout(reconnectTimer)
  if (imConn) imConn.disconnect()
})
</script>

<style scoped>
.dm-page { display: flex; height: calc(100vh - 60px); background: var(--sq-bg, #f5f7fa); }
.conv-list { width: 300px; border-right: 1px solid var(--sq-border, #e4e7ed); overflow-y: auto; background: var(--sq-card, #fff); }
.conv-head { padding: 14px 16px; font-weight: 600; border-bottom: 1px solid var(--sq-border, #eee); }
.conv-empty { padding: 30px 16px; color: #999; font-size: 13px; text-align: center; }
.conv-item { display: flex; gap: 10px; padding: 12px 16px; cursor: pointer; }
.conv-item:hover { background: var(--sq-hover, #f2f6fc); }
.conv-item.active { background: var(--sq-blue-light, #e8f1ff); }
.conv-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.conv-main { flex: 1; min-width: 0; }
.conv-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.conv-nick { font-weight: 500; font-size: 14px; }
.conv-time { font-size: 11px; color: #aaa; flex-shrink: 0; }
.conv-last { font-size: 12px; color: #888; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 170px; }
.conv-unread { background: #f56c6c; color: #fff; font-size: 11px; border-radius: 9px; padding: 0 6px; min-width: 16px; text-align: center; flex-shrink: 0; }
.chat-panel { flex: 1; display: flex; flex-direction: column; background: var(--sq-card, #fff); }
.chat-head { padding: 14px 20px; font-weight: 600; border-bottom: 1px solid var(--sq-border, #eee); }
.chat-placeholder { flex: 1; display: flex; align-items: center; justify-content: center; color: #aaa; }
.chat-msgs { flex: 1; overflow-y: auto; padding: 16px 20px; display: flex; flex-direction: column; gap: 12px; }
.dm-msg { max-width: 65%; align-self: flex-start; }
.dm-msg.self { align-self: flex-end; }
.dm-content { background: var(--sq-hover, #f2f3f5); padding: 8px 12px; border-radius: 10px; word-break: break-word; font-size: 14px; }
.dm-msg.self .dm-content { background: var(--sq-blue, #409eff); color: #fff; }
.dm-time { font-size: 11px; color: #bbb; margin-top: 3px; text-align: center; }
.chat-input-row { display: flex; gap: 10px; padding: 12px 20px; border-top: 1px solid var(--sq-border, #eee); }
</style>
