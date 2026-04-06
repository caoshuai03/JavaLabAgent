<template>
  <div class="chat-input-container">
    <div class="input-wrapper" :class="{ expanded: isExpanded }">
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
const isExpanded = ref(false)

const MIN_HEIGHT = 24
const MAX_HEIGHT = 320
const EXPAND_TRIGGER_HEIGHT = 84
const COLLAPSE_TRIGGER_HEIGHT = 56

// 将同一帧内的多次高度刷新合并，避免输入过程中出现抖动
let inputVisualSyncFrameId = 0

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !chatStore.isStreaming
})

// 每个会话独立维护自己的流任务，避免切换历史会话时互相覆盖
const streamTasks = new Map()

const getOrCreateActiveConversationKey = () => {
  return chatStore.activeConversationKey || chatStore.createConversation()
}

const getStreamTask = (conversationKey = chatStore.activeConversationKey) => {
  if (!conversationKey) return null
  return streamTasks.get(conversationKey) || null
}

const moveStreamTask = (sourceConversationKey, targetConversationKey) => {
  if (!sourceConversationKey || !targetConversationKey || sourceConversationKey === targetConversationKey) {
    return
  }

  const streamTask = streamTasks.get(sourceConversationKey)
  if (!streamTask) return

  streamTasks.delete(sourceConversationKey)
  streamTask.conversationKey = targetConversationKey
  streamTasks.set(targetConversationKey, streamTask)
}

const bindSessionToStreamTask = (newSessionId, streamTask) => {
  if (!newSessionId || !streamTask || streamTask.sessionIdReceived) {
    return
  }

  const sourceConversationKey = streamTask.conversationKey
  streamTask.sessionIdReceived = true

  if (chatStore.isDraftConversationKey(sourceConversationKey)) {
    chatStore.setCurrentSessionId(newSessionId, sourceConversationKey)
    chatStore.addNewConversationToList(newSessionId, streamTask.currentUserMessage)
    moveStreamTask(sourceConversationKey, newSessionId)
    return
  }

  if (sourceConversationKey !== newSessionId) {
    chatStore.setCurrentSessionId(newSessionId, sourceConversationKey)
    moveStreamTask(sourceConversationKey, newSessionId)
  }
}

const finalizeStreamTask = async (conversationKey, { abort = false, refreshConversations = true } = {}) => {
  if (!conversationKey) return

  const streamTask = streamTasks.get(conversationKey)

  if (abort && streamTask?.abortController) {
    streamTask.abortController.abort()
  }

  chatStore.setConversationStreaming(conversationKey, false)
  chatStore.setConversationLoading(conversationKey, false)
  streamTasks.delete(conversationKey)

  if (refreshConversations) {
    await chatStore.loadConversationsFromDB()
  }
}

const setChatMode = (mode) => {
  if (!chatStore.isStreaming) {
    chatStore.chatMode = mode
  }
}

// 长文本输入时切换到更高的编辑态，并通过滞后阈值避免临界高度反复抖动
const updateExpandedState = (contentHeight) => {
  if (isExpanded.value) {
    isExpanded.value = contentHeight > COLLAPSE_TRIGGER_HEIGHT
    return
  }

  isExpanded.value = contentHeight > EXPAND_TRIGGER_HEIGHT
}

// 统一重置输入框的视觉状态，避免发送后仍保持长文本编辑态
const resetInputVisualState = () => {
  if (!inputRef.value) return

  if (inputVisualSyncFrameId) {
    cancelAnimationFrame(inputVisualSyncFrameId)
    inputVisualSyncFrameId = 0
  }

  inputRef.value.style.height = `${MIN_HEIGHT}px`
  inputRef.value.scrollTop = 0
  showScrollbar.value = false
  isExpanded.value = false
}

// 统一使用动画帧同步高度，避免输入事件和侦听器重复更新造成动画不连贯
const scheduleInputVisualSync = () => {
  if (inputVisualSyncFrameId) {
    cancelAnimationFrame(inputVisualSyncFrameId)
  }

  inputVisualSyncFrameId = requestAnimationFrame(() => {
    inputVisualSyncFrameId = 0
    syncInputVisualState()
  })
}

// 在 DOM 完成更新后同步输入框高度与容器状态，避免删减内容后外层仍停留在高态
const syncInputVisualState = () => {
  if (!inputRef.value) return

  if (!inputText.value) {
    resetInputVisualState()
    return
  }

  // 先记录当前像素高度，再过渡到目标高度，确保大段粘贴时也能看到展开动画
  const currentHeight = Math.max(
    MIN_HEIGHT,
    parseFloat(window.getComputedStyle(inputRef.value).height) || MIN_HEIGHT,
  )

  inputRef.value.style.height = 'auto'
  const scrollHeight = inputRef.value.scrollHeight
  const newHeight = Math.max(MIN_HEIGHT, Math.min(scrollHeight, MAX_HEIGHT))
  showScrollbar.value = scrollHeight > MAX_HEIGHT
  updateExpandedState(newHeight)

  if (Math.abs(newHeight - currentHeight) < 1) {
    inputRef.value.style.height = `${newHeight}px`
    return
  }

  inputRef.value.style.height = `${currentHeight}px`
  void inputRef.value.offsetHeight
  inputRef.value.style.height = `${newHeight}px`
}

const handleInput = () => {
  scheduleInputVisualSync()
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

  const conversationKey = getOrCreateActiveConversationKey()
  const streamTask = {
    conversationKey,
    currentUserMessage: message,
    sessionIdReceived: false,
    mode: chatStore.chatMode,
    abortController: null,
  }

  chatStore.addMessage('user', message, conversationKey)

  inputText.value = ''
  resetInputVisualState()

  chatStore.addMessage('assistant', '', conversationKey)
  chatStore.setConversationStreaming(conversationKey, true)
  chatStore.setConversationLoading(conversationKey, true)

  const sessionId = chatStore.isDraftConversationKey(conversationKey) ? '' : conversationKey
  const userId = userStore.userInfo?.id || 1
  const model = chatStore.selectedModel
  const sendMessage = streamTask.mode === 'agent' ? sendReactAgentMessage : sendChatMessage

  streamTask.abortController = sendMessage(
    { message, sessionId, userId, model },
    {
      onMessage: (data) => {
        const lastMessage = chatStore.getLastMessage(streamTask.conversationKey)
        if (!lastMessage) return

        if (typeof data === 'object' && data.eventType) {
          handleStreamEvent(data, streamTask, lastMessage)
          return
        }

        if (typeof data !== 'string') {
          return
        }

        if (data.startsWith('[ERROR]')) {
          lastMessage.content = '错误: ' + data.substring(7)
          chatStore.updateLastMessage(lastMessage.content, streamTask.conversationKey)
          void finalizeStreamTask(streamTask.conversationKey, { abort: true })
          return
        }

        if (!streamTask.sessionIdReceived && data.startsWith('[SESSION_ID:')) {
          const match = data.match(/\[SESSION_ID:(.+?)\]/)
          if (match) {
            bindSessionToStreamTask(match[1], streamTask)
          }
          return
        }

        lastMessage.content += data
        chatStore.updateLastMessage(lastMessage.content, streamTask.conversationKey)
      },
      onError: (error) => {
        console.error('请求错误:', error)
        const lastMessage = chatStore.getLastMessage(streamTask.conversationKey)
        if (lastMessage && !lastMessage.content.trim()) {
          lastMessage.content = '连接错误，请重试'
          chatStore.updateLastMessage(lastMessage.content, streamTask.conversationKey)
        }
        void finalizeStreamTask(streamTask.conversationKey)
      },
      onComplete: () => {
        void finalizeStreamTask(streamTask.conversationKey, { refreshConversations: true })
      },
    },
  )

  streamTasks.set(conversationKey, streamTask)
}

const handleStreamEvent = (event, streamTask, lastMessage) => {
  const payload = event.payload || {}

  if (event.eventType === 'session') {
    const newSessionId = payload.sessionId || event.sessionId
    bindSessionToStreamTask(newSessionId, streamTask)
    return
  }

  if (event.eventType === 'token') {
    const content = payload.content || ''
    lastMessage.content += content
    chatStore.updateLastMessage(lastMessage.content, streamTask.conversationKey)
    return
  }

  if (
    event.eventType === 'tool_call' ||
    event.eventType === 'tool_result' ||
    event.eventType === 'status' ||
    event.eventType === 'skill_loaded'
  ) {
    chatStore.addToolEventToLastMessage({
      eventType: event.eventType,
      payload,
      ts: event.ts,
    }, streamTask.conversationKey)
    return
  }

  if (event.eventType === 'error') {
    const message = payload.message || '请求失败'
    if (!lastMessage.content) {
      lastMessage.content = `错误: ${message}`
      chatStore.updateLastMessage(lastMessage.content, streamTask.conversationKey)
    }
    return
  }

  if (event.eventType === 'final') {
    void finalizeStreamTask(streamTask.conversationKey, { refreshConversations: true })
  }
}

const handleStop = async (conversationKey = chatStore.activeConversationKey) => {
  const streamTask = getStreamTask(conversationKey)
  if (!streamTask && !chatStore.isStreaming) {
    return
  }

  await finalizeStreamTask(conversationKey, { abort: true })
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

watch(inputText, () => {
  // 无论是新增还是删减内容，都在下一轮渲染后重新同步一次视觉高度
  nextTick(() => {
    scheduleInputVisualSync()
  })
})

onMounted(() => {
  resetInputVisualState()
})

onUnmounted(() => {
  if (inputVisualSyncFrameId) {
    cancelAnimationFrame(inputVisualSyncFrameId)
    inputVisualSyncFrameId = 0
  }

  // 组件卸载时统一清理仍在进行中的流任务，避免留下悬挂状态
  streamTasks.forEach((streamTask, conversationKey) => {
    streamTask.abortController?.abort()
    chatStore.setConversationStreaming(conversationKey, false)
    chatStore.setConversationLoading(conversationKey, false)
  })
  streamTasks.clear()
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
    gap: 10px;
    align-items: stretch;
    position: relative;
    overflow: hidden;
    background: linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.98) 0%,
      rgba(250, 250, 252, 0.95) 100%
    );
    border: 1px solid rgba(229, 231, 235, 1);
    border-radius: 32px;
    min-height: 96px;
    padding: 16px 18px 12px 18px;
    box-shadow:
      0 8px 22px rgba(17, 24, 39, 0.05),
      0 1px 0 rgba(255, 255, 255, 0.88) inset;
    backdrop-filter: blur(14px);
    -webkit-backdrop-filter: blur(14px);
    /* 放慢容器展开与收起的动画，减少突兀感 */
    transition:
      border-color 0.44s ease,
      box-shadow 0.44s ease,
      transform 0.44s ease,
      min-height 0.52s cubic-bezier(0.22, 1, 0.36, 1),
      border-radius 0.52s cubic-bezier(0.22, 1, 0.36, 1),
      padding 0.52s cubic-bezier(0.22, 1, 0.36, 1),
      gap 0.52s cubic-bezier(0.22, 1, 0.36, 1);

    &.expanded {
      min-height: 144px;
      border-radius: 28px;
    }
  }

  @media (max-width: 768px) {
    padding: 0 16px 0 16px;

    .input-wrapper {
      gap: 8px;
      min-height: 88px;
      padding: 14px 14px 10px 14px;
      border-radius: 28px;

      &.expanded {
        min-height: 132px;
        border-radius: 24px;
      }
    }
  }
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 0;

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
  flex: none;
  padding: 0;
  background-color: transparent;
  border: none;
  color: var(--input-text);
  font-size: 16px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  min-height: 24px;
  max-height: 320px;
  overflow-y: hidden;
  outline: none;
  margin-bottom: 2px;
  /* 放慢输入区高度变化，让长文本展开更接近大厂产品的手感 */
  transition: height 0.52s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: height;

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
