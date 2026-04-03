<template>
  <div class="message-list-wrapper">
    <div class="message-list" ref="messageListRef" @scroll="handleScroll">
      <MessageItem v-for="message in chatStore.messages" :key="message.id" :message="message" />

      <div v-if="chatStore.isStreaming" class="typing-indicator">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>

    <!-- 到底部按钮 -->
    <button
      v-if="showScrollToBottomButton"
      @click="handleScrollToBottom"
      class="scroll-to-bottom-button"
    >
      <svg
        viewBox="0 0 24 24"
        width="20"
        height="20"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <line x1="12" y1="5" x2="12" y2="19"></line>
        <polyline points="19 12 12 19 5 12"></polyline>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { useChatStore } from '../stores/chat'
import MessageItem from './MessageItem.vue'

const chatStore = useChatStore()
const messageListRef = ref(null)

// 用户是否主动向上滚动过（离开底部区域）
const userHasScrolledUp = ref(false)
const showScrollToBottomButton = ref(false)

const BOTTOM_THRESHOLD = 100

// 检查当前是否在底部区域
const checkIsAtBottom = () => {
  if (!messageListRef.value) return false

  const { scrollTop, scrollHeight, clientHeight } = messageListRef.value
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight

  return distanceFromBottom <= BOTTOM_THRESHOLD
}

let scrollTimer = null
let isProgrammaticScroll = false

const handleScroll = () => {
  // 程序主动设置 scrollTop 时会触发 scroll 事件，这里跳过，避免误判为用户手动滚动
  if (isProgrammaticScroll) {
    return
  }

  // 清除之前的定时器
  if (scrollTimer) {
    clearTimeout(scrollTimer)
  }

  // 延迟检查，避免频繁触发
  scrollTimer = setTimeout(() => {
    if (!messageListRef.value) return

    const isAtBottom = checkIsAtBottom()

    if (isAtBottom) {
      // 用户滚动到底部了，恢复自动跟随
      userHasScrolledUp.value = false
      showScrollToBottomButton.value = false
    } else {
      // 用户不在底部，标记为已上滑
      userHasScrolledUp.value = true
      showScrollToBottomButton.value = true
    }
  }, 100)
}

// 滚动到底部
const scrollToBottom = (force = false) => {
  if (!messageListRef.value) return

  // 只有在强制滚动或用户未主动上滑时才自动滚动
  if (force || !userHasScrolledUp.value) {
    // 使用 nextTick 确保 DOM 已更新，再加一个 setTimeout 确保渲染完成
    nextTick(() => {
      setTimeout(() => {
        if (messageListRef.value) {
          // 标记为程序滚动，防止被 handleScroll 误判
          isProgrammaticScroll = true
          messageListRef.value.scrollTop = messageListRef.value.scrollHeight
          showScrollToBottomButton.value = false

          // 下一帧恢复，保证后续真实用户滚动仍然能被识别
          requestAnimationFrame(() => {
            isProgrammaticScroll = false
          })
        }
      }, 0)
    })
  }
}

// 点击"到底部"按钮
const handleScrollToBottom = () => {
  userHasScrolledUp.value = false // 清除上滑标记，恢复自动跟随
  scrollToBottom(true)
}

// 监听消息列表长度变化（新消息添加时）
watch(
  () => chatStore.messages.length,
  () => {
    scrollToBottom()
  },
)

// 监听最后一条消息的内容变化（流式输出文本时）
watch(
  () => {
    const messages = chatStore.messages
    if (messages.length === 0) return ''
    const lastMessage = messages[messages.length - 1]
    return lastMessage ? lastMessage.content : ''
  },
  () => {
    scrollToBottom()
  },
)

// 监听最后一条消息中的工具事件变化，确保工具列表流式渲染时也能自动跟随到底部
watch(
  () => {
    const messages = chatStore.messages
    if (messages.length === 0) return 0
    const lastMessage = messages[messages.length - 1]
    return lastMessage?.toolEvents?.length || 0
  },
  () => {
    scrollToBottom()
  },
)

// 切换历史会话时重置滚动跟随状态，确保回到流式中的会话时能直接看到最新内容
watch(
  () => chatStore.activeConversationKey,
  () => {
    userHasScrolledUp.value = false
    showScrollToBottomButton.value = false
    scrollToBottom(true)
  },
)

// 初始化：检查初始位置
onMounted(() => {
  nextTick(() => {
    if (messageListRef.value) {
      const isAtBottom = checkIsAtBottom()
      if (!isAtBottom) {
        showScrollToBottomButton.value = true
        userHasScrolledUp.value = true
      }
    }
  })
})
</script>

<style lang="scss" scoped>
.message-list-wrapper {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 60px 0 32px 0;

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

  .typing-indicator {
    display: flex;
    gap: 4px;
    padding: 20px;
    justify-content: center;

    span {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: var(--text-secondary);
      animation: typing 1.4s infinite;

      &:nth-child(2) {
        animation-delay: 0.2s;
      }

      &:nth-child(3) {
        animation-delay: 0.4s;
      }
    }
  }
}

.scroll-to-bottom-button {
  position: absolute;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background-color: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.08);
  color: #333333;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 10;

  svg {
    transition: transform 0.2s ease;
  }

  &:hover {
    background-color: #ffffff;
    border-color: rgba(0, 0, 0, 0.12);
    transform: translateX(-50%) translateY(-2px);

    svg {
      transform: translateY(1px);
    }
  }

  &:active {
    transform: translateX(-50%) translateY(0);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }
}

@keyframes typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}
</style>
