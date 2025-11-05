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

// 滚动状态
const userScrolled = ref(false) // 用户是否手动滚动
const isAtBottom = ref(true) // 是否在底部
const showScrollToBottomButton = ref(false) // 是否显示"到底部"按钮
const autoScrollEnabled = ref(true) // 是否启用自动滚动

// 距离底部的阈值（像素）
const BOTTOM_THRESHOLD = 100

// 检查是否接近底部
const checkIsAtBottom = () => {
  if (!messageListRef.value) return false
  
  const { scrollTop, scrollHeight, clientHeight } = messageListRef.value
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight
  
  return distanceFromBottom <= BOTTOM_THRESHOLD
}

// 滚动事件处理（节流）
let scrollTimer = null
let isUserScrolling = false // 标记用户是否正在主动滚动
let scrollTimeout = null

const handleScroll = () => {
  // 标记用户正在滚动
  isUserScrolling = true
  
  // 清除之前的超时
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
    
    // 如果之前不在底部，现在滚动到底部了，恢复自动滚动
    if (!wasAtBottom && isAtBottom.value) {
      userScrolled.value = false
      autoScrollEnabled.value = true
      showScrollToBottomButton.value = false
    } else if (!isAtBottom.value) {
      // 如果不在底部，标记为用户手动滚动，禁用自动滚动
      userScrolled.value = true
      autoScrollEnabled.value = false
      showScrollToBottomButton.value = true
    }
  }, 100)
}

// 滚动到底部
const scrollToBottom = (force = false) => {
  if (!messageListRef.value) return
  
  // 如果启用自动滚动或者是强制滚动，则滚动到底部
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

// 监听消息变化
watch(
  () => chatStore.messages.length,
  () => {
    // 新消息到达时，只有在启用自动滚动且用户没有正在滚动时才滚动
    if (autoScrollEnabled.value && !isUserScrolling) {
      scrollToBottom()
    }
  }
)

// 监听最后一条消息的内容变化（流式更新）
watch(
  () => {
    const messages = chatStore.messages
    if (messages.length === 0) return ''
    const lastMessage = messages[messages.length - 1]
    return lastMessage ? lastMessage.content : ''
  },
  () => {
    // 只有在流式输出、启用自动滚动、且用户没有正在滚动时才自动滚动
    if (chatStore.isStreaming && autoScrollEnabled.value && !isUserScrolling) {
      scrollToBottom()
    }
  }
)

// 监听流式状态变化
watch(
  () => chatStore.isStreaming,
  (isStreaming) => {
    // 开始流式输出时，如果启用自动滚动且用户没有正在滚动，则滚动到底部
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
  padding: 24px 0;
  
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

