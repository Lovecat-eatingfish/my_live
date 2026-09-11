<template>
  <div class="chat-input-row">
    <input
      v-model="text"
      class="chat-input"
      :placeholder="placeholder"
      maxlength="100"
      @keyup.enter="handleSend"
    />
    <el-button type="primary" size="small" @click="handleSend" :disabled="!text.trim()">
      发送
    </el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  placeholder: { type: String, default: '说点什么...' }
})

const emit = defineEmits(['send'])
const text = ref('')

function handleSend() {
  const msg = text.value.trim()
  if (!msg) return
  emit('send', msg)
  text.value = ''
}
</script>

<style scoped>
.chat-input-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border-top: 1px solid #222;
  background: #161625;
}
.chat-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #333;
  border-radius: 20px;
  background: #1a1a2e;
  color: #fff;
  font-size: 14px;
  outline: none;
}
.chat-input:focus { border-color: #667eea; }
.chat-input::placeholder { color: #555; }
</style>
