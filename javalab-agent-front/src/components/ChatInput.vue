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
          <svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect></svg>
        </button>
        <button
          v-else
          @click="handleSend"
          :disabled="!canSend"
          class="action-button send-button"
          title="发送 (Enter)"
        >
          <svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" class="h-4 w-4" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../stores/chat'
import { useUserStore } from '../stores/user'
import { sendChatMessage } from '../api/chat'

const chatStore = useChatStore()
const inputText = ref('')
const inputRef = ref(null)
const showScrollbar = ref(false)

// 高度常量配置
const MIN_HEIGHT = 24 // 文字行高
const MAX_HEIGHT = 200 // 最大高度

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !chatStore.isStreaming
})

// AbortController 用于取消 POST 请求
let abortController = null

const handleInput = () => {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    const scrollHeight = inputRef.value.scrollHeight
    
    // 这里的24是基础高度，根据内容调整
    let newHeight = Math.max(MIN_HEIGHT, Math.min(scrollHeight, MAX_HEIGHT))
    inputRef.value.style.height = newHeight + 'px'

    showScrollbar.value = scrollHeight > MAX_HEIGHT
  }
}

const handleKeyDown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (canSend.value) {
      handleSend()
    }
  }
}

// 获取用户 Store
const userStore = useUserStore()

// 是否已经接收到 sessionId
let sessionIdReceived = false
// 当前用户消息（用于设置会话标题）
let currentUserMessage = ''

/**
 * 发送消息到后端持久化接口（POST方式）
 * 使用 fetch + ReadableStream 处理 SSE 流式响应
 * 后端会将消息存储到数据库中
 */
const handleSend = async () => {
  if (!canSend.value) return

  const message = inputText.value.trim()
  if (!message) return

  // 保存当前用户消息，用于设置会话标题
  currentUserMessage = message
  sessionIdReceived = false

  // 添加用户消息到 UI
  chatStore.addMessage('user', message)

  // 清空输入框
  inputText.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = MIN_HEIGHT + 'px'
    showScrollbar.value = false
  }

  // 添加空的助手消息用于流式更新
  chatStore.addMessage('assistant', '')

  chatStore.isStreaming = true
  chatStore.isLoading = true

  // 构建请求参数
  const sessionId = chatStore.currentConversationId || ''
  const userId = userStore.userInfo?.id || 1

  // 使用 POST 方式发送请求（通过 sendChatMessage API）
  abortController = sendChatMessage(
    { message, sessionId, userId },
    {
      // 收到消息的回调
      onMessage: (data) => {
        const lastMessage = chatStore.messages[chatStore.messages.length - 1]
        if (!lastMessage) return

        // 检查是否是错误消息
        if (data.startsWith('[ERROR]')) {
          lastMessage.content = '错误：' + data.substring(7)
          handleStop()
          return
        }

        // 检查是否是结束标记
        if (data === '[DONE]' || data.trim() === '') {
          handleStop()
          return
        }

        // 检查是否是 SessionId 响应（第一条消息）
        // 格式: [SESSION_ID:xxx]
        if (!sessionIdReceived && data.startsWith('[SESSION_ID:')) {
          const match = data.match(/\[SESSION_ID:(.+?)\]/)
          if (match) {
            const newSessionId = match[1]
            sessionIdReceived = true
            
            // 如果是新会话，更新 sessionId 并添加到会话列表
            if (chatStore.isNewConversation || !chatStore.currentConversationId) {
              chatStore.setCurrentSessionId(newSessionId)
              chatStore.addNewConversationToList(newSessionId, currentUserMessage)
            }
          }
          return // 不把 sessionId 消息显示到 UI
        }

        // 更新最后一条消息
        lastMessage.content += data
        chatStore.updateLastMessage(lastMessage.content)
      },
      // 发生错误的回调
      onError: (error) => {
        console.error('请求错误:', error)
        const lastMessage = chatStore.messages[chatStore.messages.length - 1]
        if (lastMessage && !lastMessage.content.trim()) {
          lastMessage.content = '连接错误，请重试'
        }
        handleStop()
      },
      // 完成的回调
      onComplete: () => {
        handleStop()
      }
    }
  )
}

/**
 * 停止流式响应
 * 使用 AbortController 取消 POST 请求
 * 完成后刷新会话列表，同步数据库的更新时间
 */
const handleStop = async () => {
  // 使用 AbortController 取消请求
  if (abortController) {
    abortController.abort()
    abortController = null
  }

  if (chatStore.isStreaming) {
    chatStore.isStreaming = false
    chatStore.isLoading = false
    
    // 刷新会话列表，获取数据库最新的 updatedAt 时间
    await chatStore.loadConversationsFromDB()
  }
}

// 监听聚焦标志，自动聚焦输入框
watch(() => chatStore.shouldFocusInput, (shouldFocus) => {
  if (shouldFocus && inputRef.value) {
    nextTick(() => {
      inputRef.value.focus()
      // 重置标志
      chatStore.shouldFocusInput = false
    })
  }
})

onMounted(() => {
  if (inputRef.value) {
    inputRef.value.style.height = MIN_HEIGHT + 'px'
  }
})

onUnmounted(() => {
  // 组件卸载时取消请求
  if (abortController) {
    abortController.abort()
  }
})
</script>

<style lang="scss" scoped>
.chat-input-container {
  padding: 0 24px 0 24px; // 底部padding移除，留给footer
  background-color: var(--bg-primary);
  // border-top: 1px solid var(--border-color); // Removed border-top
  transition: background-color 0.3s ease, border-color 0.3s ease;

  .input-wrapper {
    max-width: 832px;
    margin: 0 auto;
    display: flex;
    gap: 12px;
    align-items: flex-end;
    position: relative;
    background-color: var(--input-bg);
    border: 1px solid var(--input-border);
    border-radius: 26px; // 圆角
    padding: 10px 10px 10px 16px; // 内边距
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
    transition: border-color 0.2s ease, box-shadow 0.2s ease;
  }
  
  @media (max-width: 768px) {
    padding: 0 16px 0 16px;
    
    .input-wrapper {
      gap: 8px;
      padding: 8px 8px 8px 12px;
    }
  }
}

.chat-input {
  flex: 1;
  padding: 0;
  background-color: transparent;
  border: none;
  color: var(--input-text);
  font-size: 16px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  min-height: 24px;
  max-height: 200px;
  overflow-y: hidden; // 默认隐藏滚动条
  outline: none;
  margin-bottom: 2px; // 对齐按钮

  // 当需要显示滚动条时
  &.has-scrollbar {
    overflow-y: auto;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &::placeholder {
    color: var(--text-secondary);
    opacity: 0.6;
  }
  
  @media (max-width: 768px) {
    font-size: 15px;
  }

  // 滚动条样式优化
  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.2);
    border-radius: 3px;
    
    &:hover {
      background: rgba(0, 0, 0, 0.3);
    }
  }
}

.input-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  
  .action-button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    padding: 0;
    border: none;
    border-radius: 50%; // 圆形
    cursor: pointer;
    transition: all 0.2s ease;
    color: white;

    &.send-button {
      background-color: #19c37d; // ChatGPT 绿色
      
      &:hover:not(:disabled) {
        background-color: #1a7f64;
      }

      &:disabled {
        background-color: #e5e5e5; // 禁用时灰色
        color: #acacac;
        cursor: not-allowed;
      }
    }

    &.stop-button {
      background-color: transparent;
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      border-radius: 50%;

      &:hover {
        background-color: var(--bg-hover);
      }
    }
  }
}
</style>
