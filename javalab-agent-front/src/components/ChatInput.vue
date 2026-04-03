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

      <div class="input-footer">
        <button
          type="button"
          class="action-button attach-button disabled-btn"
          v-tooltip="'附件上传功能开发中'"
          aria-label="附件上传功能开发中"
        >
          <svg
            stroke="currentColor"
            fill="none"
            stroke-width="2.2"
            viewBox="0 0 24 24"
            stroke-linecap="round"
            stroke-linejoin="round"
            height="1em"
            width="1em"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path d="M12 5v14"></path>
            <path d="M5 12h14"></path>
          </svg>
        </button>

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
            v-tooltip="'停止生成'"
            @click="handleStop"
          >
            <svg
              stroke="currentColor"
              fill="none"
              stroke-width="2"
              viewBox="0 0 24 24"
              stroke-linecap="round"
              stroke-linejoin="round"
              height="1em"
              width="1em"
              xmlns="http://www.w3.org/2000/svg"
            >
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
            </svg>
          </button>
          <button
            v-else
            class="action-button send-button"
            :class="{ 'disabled-btn': !canSend }"
            v-tooltip="
              chatStore.chatMode === 'agent' ? '以 Agent 模式发送(Enter)' : '以 Ask 模式发送(Enter)'
            "
            @click="handleSend"
          >
            <svg
              stroke="currentColor"
              fill="none"
              stroke-width="2.5"
              viewBox="0 0 24 24"
              stroke-linecap="round"
              stroke-linejoin="round"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path d="M12 20V4M5 11l7-7 7 7" />
            </svg>
          </button>
        </div>
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
      },
    },
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

  if (event.eventType === 'skill_loaded') {
    chatStore.addToolEventToLastMessage({
      eventType: event.eventType,
      payload,
      ts: event.ts,
    })
    return
  }

  if (
    event.eventType === 'tool_call' ||
    event.eventType === 'tool_result' ||
    event.eventType === 'status'
  ) {
    chatStore.addToolEventToLastMessage({
      eventType: event.eventType,
      payload,
      ts: event.ts,
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

watch(
  () => chatStore.shouldFocusInput,
  (shouldFocus) => {
    if (shouldFocus && inputRef.value) {
      nextTick(() => {
        inputRef.value.focus()
        chatStore.shouldFocusInput = false
      })
    }
  },
)

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
  transition:
    background-color 0.3s ease,
    border-color 0.3s ease;

  .input-wrapper {
    max-width: 952px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 14px;
    align-items: stretch;
    position: relative;
    overflow: hidden;
    background: linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.98) 0%,
      rgba(250, 250, 252, 0.95) 100%
    );
    border: 1px solid rgba(229, 231, 235, 1);
    border-radius: 42px;
    min-height: 144px;
    padding: 20px 20px 16px 20px;
    box-shadow:
      0 8px 22px rgba(17, 24, 39, 0.05),
      0 1px 0 rgba(255, 255, 255, 0.88) inset;
    backdrop-filter: blur(14px);
    -webkit-backdrop-filter: blur(14px);
    transition:
      border-color 0.2s ease,
      box-shadow 0.2s ease,
      transform 0.2s ease;
  }

  @media (max-width: 768px) {
    padding: 0 16px 0 16px;

    .input-wrapper {
      gap: 10px;
      min-height: 128px;
      padding: 16px 14px 12px 14px;
      border-radius: 34px;
    }
  }
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;

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

    &.attach-button {
      width: 24px;
      height: 24px;
      background-color: transparent;
      border: none;
      color: #999;
      cursor: not-allowed;
      flex-shrink: 0;

      &.disabled-btn {
        opacity: 0.8;
      }

      &:hover:not(.disabled-btn) {
        color: #666;
      }

      svg {
        width: 20px;
        height: 20px;
        stroke-width: 2;
      }
    }
  }
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  align-items: center;
  position: relative;
  width: 111px;
  padding: 3px;
  border-radius: 999px;
  background: rgba(144, 19, 139, 0.05);
  border: none;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.65) inset,
    0 1px 3px rgba(144, 19, 139, 0.06);
  flex-shrink: 0;
  transition:
    box-shadow 0.25s ease,
    transform 0.2s ease;

  .mode-slider {
    position: absolute;
    top: 3px;
    left: 3px;
    width: calc((100% - 6px) / 2);
    height: calc(100% - 6px);
    border-radius: 999px;
    background: linear-gradient(180deg, rgba(144, 19, 139, 0.16) 0%, rgba(144, 19, 139, 0.1) 100%);
    box-shadow: 0 1px 2px rgba(144, 19, 139, 0.08);
    transition:
      transform 0.32s cubic-bezier(0.22, 1, 0.36, 1),
      background-color 0.28s ease,
      box-shadow 0.28s ease;
    pointer-events: none;
  }

  &.mode-agent .mode-slider {
    transform: translateX(100%);
  }

  &.disabled {
    opacity: 0.55;
  }

  .mode-option {
    width: 100%;
    height: 30px;
    padding: 0 10px;
    border: none;
    border-radius: 999px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 14px;
    font-family: inherit;
    font-weight: 500;
    line-height: 30px;
    cursor: pointer;
    position: relative;
    z-index: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    vertical-align: middle;
    outline: none;
    transition:
      color 0.24s ease,
      transform 0.22s ease;

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
      color: #5f115c;
      font-weight: 600;

      &:hover:not(:disabled) {
        color: #5f115c;
      }
    }
  }

  @media (max-width: 768px) {
    width: 120px;
    padding: 3px;

    .mode-option {
      padding: 0 8px;
      font-size: 13px;
      height: 28px;
      line-height: 28px;
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
  gap: 6px;
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
      width: 32px;
      height: 32px;
      background-color: #90138b;

      svg {
        width: 1.2em;
        height: 1.2em;
      }

      &:hover:not(.disabled-btn) {
        background-color: #9b2a96;
      }

      &.disabled-btn {
        background-color: #e5e5ea;
        color: #8e8e93;
        cursor: not-allowed;
      }
    }

    &.stop-button {
      width: 24px;
      height: 24px;
      background-color: transparent;
      color: var(--text-primary);
      border: 1px solid var(--border-color);

      svg {
        width: 1em;
        height: 1em;
      }

      &:hover {
        background-color: var(--bg-hover);
      }
    }
  }
}

.chat-tooltip {
  position: fixed;
  transform: translateX(-50%) translateY(-100%);
  background: rgba(17, 24, 39, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: #fff;
  padding: 8px 14px;
  border-radius: 12px; /* 更加圆润 */
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
  pointer-events: none;
  z-index: 3000;
  animation: chatTooltipFadeIn 0.2s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  box-shadow:
    0 4px 16px rgba(0, 0, 0, 0.15),
    0 1px 2px rgba(255, 255, 255, 0.1) inset;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

@keyframes chatTooltipFadeIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-100%) translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(-100%) translateY(0);
  }
}
</style>
