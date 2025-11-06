<template>
  <div class="chat-input-container">
    <div class="input-wrapper">
      <textarea
        v-model="inputText"
        @keydown="handleKeyDown"
        @input="handleInput"
        :disabled="chatStore.isStreaming"
        :placeholder="chatStore.isStreaming ? 'AI 正在回复...' : '输入消息...'"
        :class="['chat-input', { 'has-scrollbar': showScrollbar }]"
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
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../stores/chat'
import apiClient from '../api'

const chatStore = useChatStore()
const inputText = ref('')
const inputRef = ref(null)
const showScrollbar = ref(false)

// 高度常量配置
const MIN_HEIGHT = 48 // 最小高度 (padding上下各12px + 一行文字高度24px)
const MAX_HEIGHT = 200 // 最大高度 (约6-7行，包含padding)

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !chatStore.isStreaming
})

let eventSource = null

const handleInput = () => {
  // 自动调整高度并管理滚动条
  if (inputRef.value) {
    // 重置高度以获取准确的scrollHeight
    inputRef.value.style.height = 'auto'
    const scrollHeight = inputRef.value.scrollHeight
    
    // 计算实际高度，限制在最小和最大高度之间
    let newHeight = Math.max(MIN_HEIGHT, Math.min(scrollHeight, MAX_HEIGHT))
    inputRef.value.style.height = newHeight + 'px'
    
    // 判断是否需要显示滚动条：当内容高度超过最大高度时显示
    showScrollbar.value = scrollHeight > MAX_HEIGHT
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
  
  // 清空输入框并重置高度
  inputText.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = MIN_HEIGHT + 'px'
    showScrollbar.value = false
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

// 组件挂载时初始化高度
onMounted(() => {
  if (inputRef.value) {
    // 初始化时设置最小高度（包含padding）
    // padding上下各12px + 一行文字高度24px = 48px
    inputRef.value.style.height = '48px'
  }
})

// 组件卸载时清理
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
    position: relative;
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
  min-height: 48px;
  max-height: 200px;
  overflow-y: hidden; // 默认隐藏滚动条
  transition: height 0.2s ease, border-color 0.2s ease, background-color 0.3s ease, color 0.3s ease, box-shadow 0.2s ease;
  
  // 当需要显示滚动条时
  &.has-scrollbar {
    overflow-y: auto;
  }
  
  &:focus {
    outline: none;
    border-color: #10a37f;
    box-shadow: 0 0 0 3px rgba(16, 163, 127, 0.1);
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  
  &::placeholder {
    color: var(--text-secondary);
    opacity: 0.6;
  }
  
  // 滚动条样式优化 - 更细更精致
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 3px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.2);
    border-radius: 3px;
    transition: background 0.2s ease;
    
    &:hover {
      background: rgba(0, 0, 0, 0.3);
    }
  }
  
  // 暗色模式下的滚动条
  @media (prefers-color-scheme: dark) {
    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.2);
      
      &:hover {
        background: rgba(255, 255, 255, 0.3);
      }
    }
  }
}

.input-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  
  .action-button {
    padding: 10px 20px;
    border: none;
    border-radius: 20px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    
    &:active {
      transform: scale(0.98);
    }
    
    &.send-button {
      background-color: #10a37f;
      color: white;
      
      &:hover:not(:disabled) {
        background-color: #0d8f6e;
        box-shadow: 0 2px 4px rgba(16, 163, 127, 0.2);
        transform: translateY(-1px);
      }
      
      &:disabled {
        background-color: var(--border-color-hover);
        color: var(--text-secondary);
        cursor: not-allowed;
        box-shadow: none;
        transform: none;
      }
    }
    
    &.stop-button {
      background-color: #dc3545;
      color: white;
      
      &:hover {
        background-color: #c82333;
        box-shadow: 0 2px 4px rgba(220, 53, 69, 0.2);
        transform: translateY(-1px);
      }
    }
  }
}
</style>


