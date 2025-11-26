<template>
  <div class="message-list-wrapper">
    <div 
      class="message-list" 
      ref="messageListRef"
      @scroll="handleScroll"
    >
      <div v-if="chatStore.messages.length === 0" class="empty-state">
        <div class="empty-content">
          <h2>开始新的对话</h2>
          <p>在下方输入框中输入您的问题，AI 助手将为您提供帮助。</p>
        </div>
      </div>
      
      <MessageItem
        v-for="message in chatStore.messages"
        :key="message.id"
        :message="message"
      />
      
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
      title="滚动到底部"
    >
      ↓
    </button>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { useChatStore } from '../stores/chat'
import MessageItem from './MessageItem.vue'

const chatStore = useChatStore()
const messageListRef = ref(null)

const userScrolled = ref(false)
const isAtBottom = ref(true)
const showScrollToBottomButton = ref(false)
const autoScrollEnabled = ref(true)

const BOTTOM_THRESHOLD = 100

const checkIsAtBottom = () => {
  if (!messageListRef.value) return false
  
  const { scrollTop, scrollHeight, clientHeight } = messageListRef.value
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight
  
  return distanceFromBottom <= BOTTOM_THRESHOLD
}

let scrollTimer = null
let isUserScrolling = false
let scrollTimeout = null

const handleScroll = () => {
  isUserScrolling = true
  
  if (scrollTimeout) {
    clearTimeout(scrollTimeout)
  }
  
  // 设置一个较短的超时，检测滚动是否停止
  scrollTimeout = setTimeout(() => {
    isUserScrolling = false
  }, 150)
  
  if (scrollTimer) {
    clearTimeout(scrollTimer)
  }
  
  scrollTimer = setTimeout(() => {
    if (!messageListRef.value) return
    
    const wasAtBottom = isAtBottom.value
    isAtBottom.value = checkIsAtBottom()
    
    if (!wasAtBottom && isAtBottom.value) {
      userScrolled.value = false
      autoScrollEnabled.value = true
      showScrollToBottomButton.value = false
    } else if (!isAtBottom.value) {
      userScrolled.value = true
      autoScrollEnabled.value = false
      showScrollToBottomButton.value = true
    }
  }, 100)
}

const scrollToBottom = (force = false) => {
  if (!messageListRef.value) return
  
  if (autoScrollEnabled.value || force) {
    nextTick(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
        isAtBottom.value = true
        userScrolled.value = false
        showScrollToBottomButton.value = false
      }
    })
  }
}

// 点击"到底部"按钮
const handleScrollToBottom = () => {
  scrollToBottom(true)
  autoScrollEnabled.value = true
  userScrolled.value = false
}

watch(
  () => chatStore.messages.length,
  () => {
    if (autoScrollEnabled.value && !isUserScrolling) {
      scrollToBottom()
    }
  }
)

watch(
  () => {
    const messages = chatStore.messages
    if (messages.length === 0) return ''
    const lastMessage = messages[messages.length - 1]
    return lastMessage ? lastMessage.content : ''
  },
  () => {
    if (chatStore.isStreaming && autoScrollEnabled.value && !isUserScrolling) {
      scrollToBottom()
    }
  }
)

watch(
  () => chatStore.isStreaming,
  (isStreaming) => {
    if (isStreaming && autoScrollEnabled.value && !isUserScrolling) {
      scrollToBottom()
    }
  }
)

// 初始化：检查初始位置
onMounted(() => {
  nextTick(() => {
    if (messageListRef.value) {
      isAtBottom.value = checkIsAtBottom()
      if (!isAtBottom.value) {
        showScrollToBottomButton.value = true
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
  padding: 32px 0;
  
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
  
  .empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    
    .empty-content {
      text-align: center;
      color: var(--text-secondary);
      
      h2 {
        color: var(--text-primary);
        font-size: 24px;
        margin-bottom: 8px;
        font-weight: 600;
      }
      
      p {
        font-size: 16px;
      }
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
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transition: all 0.2s;
  z-index: 10;
  
  &:hover {
    background-color: var(--bg-hover);
    border-color: var(--border-color-hover);
    transform: translateX(-50%) translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  }
  
  &:active {
    transform: translateX(-50%) translateY(0);
  }
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}
</style>
