<template>
  <div class="chat-input-container">
    <div class="input-wrapper">
      <textarea
        ref="inputRef"
        v-model="inputText"
        :disabled="chatStore.isStreaming"
        :placeholder="chatStore.isStreaming ? 'AI 正在回复...' : '输入消息...'"
        :class="['chat-input', { 'has-scrollbar': showScrollbar }]"
        rows="1"
        @keydown="handleKeyDown"
        @input="handleInput"
      ></textarea>

      <div class="input-actions">
        <div
          v-if="!chatStore.isStreaming"
          class="mode-switch"
          :class="[{ disabled: chatStore.isStreaming }, `mode-${chatStore.chatMode}`]"
        >
          <span class="mode-slider" aria-hidden="true"></span>
          <button
            type="button"
            class="mode-option"
            :class="{ active: chatStore.chatMode === 'ask' }"
            :disabled="chatStore.isStreaming"
            @click="setChatMode('ask')"
          >
            Ask
          </button>
          <button
            type="button"
            class="mode-option"
            :class="{ active: chatStore.chatMode === 'agent' }"
            :disabled="chatStore.isStreaming"
            @click="setChatMode('agent')"
          >
            Agent
          </button>
        </div>

        <button
          v-if="chatStore.isStreaming"
          class="action-button stop-button"
          title="停止生成"
          @click="handleStop"
        >
          <svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect></svg>
        </button>
        <button
          v-else
          class="action-button send-button"
          :disabled="!canSend"
          :title="chatStore.chatMode === 'agent' ? '以 Agent 模式发送(Enter)' : '以 Ask 模式发送(Enter)'"
          @click="handleSend"
        >
          <svg stroke="currentColor" fill="none" stroke-width="2.5" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="1.2em" width="1.2em" xmlns="http://www.w3.org/2000/svg">
            <path d="m5 12 7-7 7 7"></path>
            <path d="M12 19V5"></path>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../stores/chat'
import { useUserStore } from '../stores/user'
import { sendChatMessage, sendReactAgentMessage } from '../api/chat'

const chatStore = useChatStore()
const userStore = useUserStore()

const inputText = ref('')
const inputRef = ref(null)
const showScrollbar = ref(false)

const MIN_HEIGHT = 24
const MAX_HEIGHT = 200

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !chatStore.isStreaming
})

let abortController = null
let sessionIdReceived = false
let currentUserMessage = ''

const setChatMode = (mode) => {
  if (!chatStore.isStreaming) {
    chatStore.chatMode = mode
  }
}

const handleInput = () => {
  if (!inputRef.value) return

  inputRef.value.style.height = 'auto'
  const scrollHeight = inputRef.value.scrollHeight
  const newHeight = Math.max(MIN_HEIGHT, Math.min(scrollHeight, MAX_HEIGHT))
  inputRef.value.style.height = `${newHeight}px`
  showScrollbar.value = scrollHeight > MAX_HEIGHT
}

const handleKeyDown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (canSend.value) {
      handleSend()
    }
  }
}

const handleSend = async () => {
  if (!canSend.value) return

  const message = inputText.value.trim()
  if (!message) return

  currentUserMessage = message
  sessionIdReceived = false

  chatStore.addMessage('user', message)

  inputText.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = `${MIN_HEIGHT}px`
    showScrollbar.value = false
  }

  chatStore.addMessage('assistant', '')
  chatStore.isStreaming = true
  chatStore.isLoading = true

  const sessionId = chatStore.currentConversationId || ''
  const userId = userStore.userInfo?.id || 1
  const model = chatStore.selectedModel
  const sendMessage = chatStore.chatMode === 'agent' ? sendReactAgentMessage : sendChatMessage

  abortController = sendMessage(
    { message, sessionId, userId, model },
    {
      onMessage: (data) => {
        const lastMessage = chatStore.messages[chatStore.messages.length - 1]
        if (!lastMessage) return

        if (chatStore.chatMode === 'agent' && typeof data === 'object' && data.eventType) {
          handleAgentEvent(data, lastMessage)
          return
        }

        if (typeof data !== 'string') {
          return
        }

        if (data.startsWith('[ERROR]')) {
          lastMessage.content = '错误: ' + data.substring(7)
          handleStop()
          return
        }

        if (!sessionIdReceived && data.startsWith('[SESSION_ID:')) {
          const match = data.match(/\[SESSION_ID:(.+?)\]/)
          if (match) {
            const newSessionId = match[1]
            sessionIdReceived = true

            if (chatStore.isNewConversation || !chatStore.currentConversationId) {
              chatStore.setCurrentSessionId(newSessionId)
              chatStore.addNewConversationToList(newSessionId, currentUserMessage)
            }
          }
          return
        }

        lastMessage.content += data
        chatStore.updateLastMessage(lastMessage.content)
      },
      onError: (error) => {
        console.error('请求错误:', error)
        const lastMessage = chatStore.messages[chatStore.messages.length - 1]
        if (lastMessage && !lastMessage.content.trim()) {
          lastMessage.content = '连接错误，请重试'
        }
        handleStop()
      },
      onComplete: () => {
        handleStop()
      }
    }
  )
}

const handleAgentEvent = (event, lastMessage) => {
  const payload = event.payload || {}

  if (event.eventType === 'session') {
    const newSessionId = payload.sessionId || event.sessionId
    if (newSessionId && !sessionIdReceived) {
      sessionIdReceived = true
      if (chatStore.isNewConversation || !chatStore.currentConversationId) {
        chatStore.setCurrentSessionId(newSessionId)
        chatStore.addNewConversationToList(newSessionId, currentUserMessage)
      }
    }
    return
  }

  if (event.eventType === 'token') {
    const content = payload.content || ''
    lastMessage.content += content
    chatStore.updateLastMessage(lastMessage.content)
    return
  }

  if (event.eventType === 'tool_call' || event.eventType === 'tool_result' || event.eventType === 'status') {
    chatStore.addToolEventToLastMessage({
      eventType: event.eventType,
      payload,
      ts: event.ts
    })
    return
  }

  if (event.eventType === 'error') {
    const message = payload.message || '请求失败'
    if (!lastMessage.content) {
      lastMessage.content = `错误: ${message}`
      chatStore.updateLastMessage(lastMessage.content)
    }
    return
  }

  if (event.eventType === 'final') {
    handleStop()
  }
}

const handleStop = async () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }

  if (chatStore.isStreaming) {
    chatStore.isStreaming = false
    chatStore.isLoading = false
    await chatStore.loadConversationsFromDB()
  }
}

watch(() => chatStore.shouldFocusInput, (shouldFocus) => {
  if (shouldFocus && inputRef.value) {
    nextTick(() => {
      inputRef.value.focus()
      chatStore.shouldFocusInput = false
    })
  }
})

onMounted(() => {
  if (inputRef.value) {
    inputRef.value.style.height = `${MIN_HEIGHT}px`
  }
})

onUnmounted(() => {
  if (abortController) {
    abortController.abort()
  }
})
</script>

<style lang="scss" scoped>
.chat-input-container {
  padding: 0 24px 0 24px;
  background-color: var(--bg-primary);
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
    border-radius: 26px;
    padding: 10px 10px 10px 12px;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
    transition: border-color 0.2s ease, box-shadow 0.2s ease;
  }

  @media (max-width: 768px) {
    padding: 0 16px 0 16px;

    .input-wrapper {
      gap: 8px;
      padding: 8px;
    }
  }
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  align-items: center;
  position: relative;
  width: 112px;
  padding: 2px;
  border-radius: 999px;
  background: transparent;
  border: 1px solid var(--input-border);
  flex-shrink: 0;
  transition: border-color 0.25s ease, box-shadow 0.25s ease;

  .mode-slider {
    position: absolute;
    top: 2px;
    left: 2px;
    width: calc((100% - 4px) / 2);
    height: calc(100% - 4px);
    border-radius: 999px;
    background: #90138B;
    box-shadow: 0 4px 12px rgba(144, 19, 139, 0.18);
    transition: transform 0.32s cubic-bezier(0.22, 1, 0.36, 1), background-color 0.28s ease, box-shadow 0.28s ease;
    pointer-events: none;
  }

  &.mode-agent .mode-slider {
    transform: translateX(100%);
  }

  &.disabled {
    opacity: 0.7;
  }

  .mode-option {
    width: 100%;
    height: 26px;
    padding: 0 8px;
    border: none;
    border-radius: 999px;
    background: transparent;
    color: var(--text-primary);
    font-size: 14px;
    font-family: inherit;
    font-weight: 600;
    line-height: 26px;
    cursor: pointer;
    position: relative;
    z-index: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    vertical-align: middle;
    outline: none;
    transition: color 0.24s ease, transform 0.22s ease;

    &:hover:not(:disabled) {
      color: var(--text-primary);
    }

    &:disabled {
      cursor: not-allowed;
    }

    &:active:not(:disabled) {
      transform: scale(0.97);
    }

    &:focus,
    &:focus-visible {
      outline: none;
      box-shadow: none;
    }

    &.active {
      color: #fff;

      &:hover:not(:disabled) {
        color: #fff;
      }
    }
  }

  @media (max-width: 768px) {
    width: 104px;

    .mode-option {
      padding: 0 8px;
      font-size: 13px;
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
  overflow-y: hidden;
  outline: none;
  margin-bottom: 2px;

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
  gap: 8px;
  flex-shrink: 0;

  .action-button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    padding: 0;
    border: none;
    border-radius: 50%;
    cursor: pointer;
    transition: all 0.2s ease;
    color: white;

    &.send-button {
      background-color: #90138B;

      &:hover:not(:disabled) {
        background-color: #9B2A96;
      }

      &:disabled {
        background-color: #e5e5e5;
        color: #acacac;
        cursor: not-allowed;
      }
    }

    &.stop-button {
      background-color: transparent;
      color: var(--text-primary);
      border: 1px solid var(--border-color);

      &:hover {
        background-color: var(--bg-hover);
      }
    }
  }
}
</style>
