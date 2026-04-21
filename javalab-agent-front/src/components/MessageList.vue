<template>
  <div class="message-list-wrapper">
    <div class="message-list" ref="messageListRef" @scroll="handleScroll" @wheel="handleWheel">
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
  return scrollHeight - scrollTop - clientHeight <= BOTTOM_THRESHOLD
}

// 标记：程序正在执行滚动，防止 handleScroll 误判
let isProgrammaticScroll = false

/**
 * 鼠标滚轮事件 —— 用户向上滚动时立即标记，
 * wheel 事件在 scroll 事件之前触发，因此不受异步延迟影响，
 * 可以在下一个 token 到达前抢先设置 userHasScrolledUp。
 */
const handleWheel = (e) => {
  if (e.deltaY < 0) {
    // deltaY < 0 表示用户向上滚动
    userHasScrolledUp.value = true
    showScrollToBottomButton.value = true
  }
}

/**
 * scroll 事件 —— 同步检查位置（不做防抖），
 * 滚回底部时恢复自动跟随；不在底部且非程序滚动时标记上滑。
 */
const handleScroll = () => {
  if (!messageListRef.value) return

  if (checkIsAtBottom()) {
    // 在底部 → 无论是程序滚动还是用户滚动，都恢复自动跟随
    userHasScrolledUp.value = false
    showScrollToBottomButton.value = false
  } else if (!isProgrammaticScroll) {
    // 不在底部 且 非程序触发 → 标记为用户主动上滑（兼容拖拽滚动条等场景）
    userHasScrolledUp.value = true
    showScrollToBottomButton.value = true
  }
}

// 滚动到底部
const scrollToBottom = (force = false) => {
  if (!messageListRef.value) return

  // 只有在强制滚动或用户未主动上滑时才自动滚动
  if (force || !userHasScrolledUp.value) {
    nextTick(() => {
      if (messageListRef.value) {
        // 标记为程序滚动，防止 handleScroll else 分支误判
        isProgrammaticScroll = true
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
        showScrollToBottomButton.value = false

        // 50ms 后重置，确保本次 scroll 事件已处理完毕
        setTimeout(() => {
          isProgrammaticScroll = false
        }, 50)
      }
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
