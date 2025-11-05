<template>
  <div class="chat-input-container">
    <div class="input-wrapper">
      <textarea
        v-model="inputText"
        @keydown="handleKeyDown"
        @input="handleInput"
        :disabled="chatStore.isStreaming"
        :placeholder="chatStore.isStreaming ? 'AI 正在回复...' : '输入消息...'"
        class="chat-input"
        ref="inputRef"
        rows="1"
      ></textarea>
      
      <div class="input-actions">
        <button
          v-if="chatStore.isStreaming"
          @click="handleStop"
          class="action-button stop-button"
          title="停止生成"
        >
          停止
        </button>
        <button
          v-else
          @click="handleSend"
          :disabled="!canSend"
          class="action-button send-button"
          title="发送 (Enter)"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'
import apiClient from '../api'

const chatStore = useChatStore()
const inputText = ref('')
const inputRef = ref(null)

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !chatStore.isStreaming
})

let eventSource = null

const handleInput = () => {
  // 自动调整高度
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    inputRef.value.style.height = inputRef.value.scrollHeight + 'px'
  }
}

const handleKeyDown = (event) => {
  // Enter 发送，Shift+Enter 换行
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (canSend.value) {
      handleSend()
    }
  }
}

const getApiBaseUrl = () => {
  if (import.meta.env.DEV) {
    return ''
  } else {
    return ''
  }
}

const handleSend = async () => {
  if (!canSend.value) return
  
  const message = inputText.value.trim()
  if (!message) return
  
  // 确保有当前会话，如果没有则创建新会话
  if (!chatStore.currentConversationId) {
    chatStore.createConversation()
  }
  
  // 添加用户消息
  chatStore.addMessage('user', message)
  
  // 清空输入框
  inputText.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
  
  // 创建 AI 消息占位符
  chatStore.addMessage('assistant', '')
  
  // 设置流式状态
  chatStore.isStreaming = true
  chatStore.isLoading = true
  
  // 开始流式请求，传递conversationId参数
  const baseUrl = getApiBaseUrl()
  const conversationId = chatStore.currentConversationId || ''
  eventSource = new EventSource(
    `${baseUrl}/api/v1/chat/stream?message=${encodeURIComponent(message)}&conversationId=${encodeURIComponent(conversationId)}`
  )
  
  eventSource.onmessage = (event) => {
    const lastMessage = chatStore.messages[chatStore.messages.length - 1]
    
    if (!lastMessage) return
    
    // 检查是否是错误消息
    if (event.data.startsWith('[ERROR]')) {
      lastMessage.content = '错误：' + event.data.substring(7)
      handleStop()
      return
    }
    
    // 检查是否是结束标记
    if (event.data === '[DONE]' || event.data.trim() === '') {
      handleStop()
      return
    }
    
    // 更新最后一条消息
    lastMessage.content += event.data
    chatStore.updateLastMessage(lastMessage.content)
  }
  
  eventSource.onerror = (error) => {
    console.error('SSE连接错误:', error)
    const lastMessage = chatStore.messages[chatStore.messages.length - 1]
    
    // 如果连接已关闭（readyState === 2），正常结束
    if (eventSource.readyState === EventSource.CLOSED) {
      handleStop()
      return
    }
    
    // 其他错误
    if (lastMessage && !lastMessage.content.trim()) {
      lastMessage.content = '连接错误，请重试'
    }
    handleStop()
  }
}

const handleStop = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  
  if (chatStore.isStreaming) {
    chatStore.isStreaming = false
    chatStore.isLoading = false
    
    // 保存当前对话的消息
    chatStore.saveCurrentConversationMessages()
  }
}

// 组件卸载时清理
import { onUnmounted } from 'vue'
onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style lang="scss" scoped>
.chat-input-container {
  padding: 16px 24px;
  background-color: var(--bg-primary);
  border-top: 1px solid var(--border-color);
  transition: background-color 0.3s ease, border-color 0.3s ease;
  
  .input-wrapper {
    max-width: 768px;
    margin: 0 auto;
    display: flex;
    gap: 12px;
    align-items: flex-end;
  }
}

.chat-input {
  flex: 1;
  padding: 12px 16px;
  background-color: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: 24px;
  color: var(--input-text);
  font-size: 16px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  max-height: 200px;
  overflow-y: auto;
  transition: border-color 0.2s, background-color 0.3s ease, color 0.3s ease;
  
  &:focus {
    outline: none;
    border-color: #10a37f;
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  
  &::placeholder {
    color: var(--text-secondary);
  }
  
  &::-webkit-scrollbar {
    width: 8px;
  }
  
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  
  &::-webkit-scrollbar-thumb {
    background: var(--scrollbar-thumb);
    border-radius: 4px;
    
    &:hover {
      background: var(--scrollbar-thumb-hover);
    }
  }
}

.input-actions {
  display: flex;
  align-items: center;
  
  .action-button {
    padding: 10px 20px;
    border: none;
    border-radius: 20px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
    
    &.send-button {
      background-color: #10a37f;
      color: white;
      
      &:hover:not(:disabled) {
        background-color: #0d8f6e;
      }
      
      &:disabled {
        background-color: var(--border-color-hover);
        color: var(--text-secondary);
        cursor: not-allowed;
      }
    }
    
    &.stop-button {
      background-color: #dc3545;
      color: white;
      
      &:hover {
        background-color: #c82333;
      }
    }
  }
}
</style>

