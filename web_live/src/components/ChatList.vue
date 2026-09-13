<template>
  <div class="chat-list" ref="listRef">
    <div
      v-for="(msg, i) in messages"
      :key="i"
      :class="['chat-item', { 'is-self': msg.isSelf }]"
    >
      <img :src="msg.avatar || defaultAvatar" class="chat-avatar" />
      <div class="chat-body">
        <div class="chat-meta">
          <span class="chat-name">{{ msg.userName }}</span>
          <span class="chat-time">{{ msg.time }}</span>
        </div>
        <div class="chat-bubble">{{ msg.content }}</div>
      </div>
    </div>
    <div v-if="messages.length === 0" class="chat-empty">暂无消息，快来聊天吧~</div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
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
</style>
