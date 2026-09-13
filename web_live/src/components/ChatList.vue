<template>
  <div class="chat-list" ref="listRef">
    <div
      v-for="(msg, i) in messages"
      :key="i"
      :class="['chat-item', { 'is-self': msg.isSelf }]"
    >
      <template v-if="msg.system">
        <div class="chat-system">{{ msg.content }}</div>
      </template>
      <template v-else>
      <img :src="msg.avatar || defaultAvatar" class="chat-avatar" @click="goProfile(msg)" />
      <div class="chat-body">
        <div class="chat-meta">
          <span v-if="msg.level" :class="['lv-badge', levelClass(msg.level)]">L{{ msg.level }}</span>
          <span class="chat-name" @click="goProfile(msg)">{{ msg.userName }}</span>
          <span class="chat-time">{{ msg.time }}</span>
          <span v-if="canMute && !msg.isSelf" class="chat-mute" title="禁言 30 分钟" @click="$emit('mute', msg)">🔇</span>
        </div>
        <div class="chat-bubble">{{ msg.content }}</div>
      </div>
      </template>
    </div>
    <div v-if="messages.length === 0" class="chat-empty">暂无消息，快来聊天吧~</div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// 头像/昵称点击进入个人主页
function goProfile(msg) {
  if (msg.userId) router.push(`/profile/${msg.userId}`)
}

// 等级徽章配色：1-4 绿 / 5-8 蓝 / 9+ 金（B 站风格）
function levelClass(level) {
  if (level >= 9) return 'lv-gold'
  if (level >= 5) return 'lv-blue'
  return 'lv-green'
}

const emit = defineEmits(['mute'])
const props = defineProps({
  canMute: { type: Boolean, default: false },
  canMute: { type: Boolean, default: false },
  messages: {
    type: Array,
    default: () => []
  }
})

const listRef = ref(null)
const defaultAvatar = 'https://via.placeholder.com/36/667eea/fff?text=U'

watch(() => props.messages.length, () => {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
})
</script>

<style scoped>
.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chat-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.chat-item.is-self {
  flex-direction: row-reverse;
}
.chat-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.chat-body { max-width: 70%; }
.chat-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 2px;
}
.chat-name { font-size: 12px; color: var(--sq-blue); }
.is-self .chat-name { color: #ffd700; }
.chat-time { font-size: 11px; color: #555; }
.chat-bubble {
  background: var(--sq-card);
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 14px;
  color: #ddd;
  line-height: 1.4;
  word-break: break-word;
}
.is-self .chat-bubble {
  background: rgba(102, 126, 234, 0.3);
}
.chat-empty {
  text-align: center;
  color: #444;
  font-size: 13px;
  padding: 40px 0;
}
/* 等级徽章 */
.lv-badge {
  display: inline-block;
  padding: 0 4px;
  margin-right: 4px;
  border-radius: 3px;
  font-size: 10px;
  line-height: 15px;
  color: #fff;
  vertical-align: 1px;
  cursor: default;
}
.lv-green { background: #67c23a; }
.lv-blue { background: #409eff; }
.lv-gold { background: #e6a23c; }
.chat-avatar { cursor: pointer; }
.chat-system {
  width: 100%; text-align: center; font-size: 12px; color: #7c8aa5;
  padding: 4px 0; background: rgba(124,138,165,0.08); border-radius: 6px; margin: 2px 0;
}
.chat-mute { cursor: pointer; opacity: 0; transition: opacity .15s; font-size: 11px; }
.chat-item:hover .chat-mute { opacity: 1; }
</style>
