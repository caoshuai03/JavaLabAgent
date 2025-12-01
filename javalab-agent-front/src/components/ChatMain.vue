<template>
  <div class="chat-main" :class="{ 'is-empty': chatStore.messages.length === 0 }" @click="handleMainClick">
    <button 
      v-if="showMobileMenuButton" 
      @click.stop="toggleSidebar" 
      class="mobile-menu-button"
      title="打开菜单"
    >
      ☰
    </button>
    
    <MessageList v-show="chatStore.messages.length > 0" />
    
    <div v-if="chatStore.messages.length === 0" class="welcome-container">
      <div class="welcome-content">
        <h2>Java实验助手</h2>
        <p>我可以为您解答Java实验相关的问题，请把您的任务交给我吧~</p>
      </div>
    </div>

    <ChatInput />

    <div class="footer-container">
      <p>AI生成内容仅供参考，不代表本平台立场。版权所有 © shuaicao01@163.com</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../stores/chat'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'

const chatStore = useChatStore()
const isMobile = ref(false)

const showMobileMenuButton = computed(() => {
  return isMobile.value && chatStore.sidebarCollapsed
})

const toggleSidebar = () => {
  chatStore.toggleSidebar()
}

const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (isMobile.value && !chatStore.sidebarCollapsed) {
    chatStore.sidebarCollapsed = true
  }
}

const handleMainClick = () => {
  if (isMobile.value && !chatStore.sidebarCollapsed) {
    chatStore.sidebarCollapsed = true
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style lang="scss" scoped>
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--bg-primary);
  overflow: hidden;
  min-width: 0;
  transition: background-color 0.3s ease;
  position: relative;
  
  &.is-empty {
    justify-content: center;
    
    .welcome-container {
      display: flex;
      justify-content: center;
      margin-bottom: 40px;
      
      .welcome-content {
        text-align: center;
        color: var(--text-secondary);
        
        h2 {
          color: var(--text-primary);
          font-size: 24px;
          margin-bottom: 16px;
          font-weight: 600;
        }
        
        p {
          font-size: 16px;
          line-height: 1.5;
        }
      }
    }

    :deep(.chat-input-container) {
      border-top: none;
      background-color: transparent;
    }
  }

  .footer-container {
    text-align: center;
    color: var(--text-tertiary, #999);
    font-size: 12px;
    padding: 8px 0 12px 0;
    width: 100%;
    flex-shrink: 0; // 防止被压缩
    
    p {
      margin: 0;
      opacity: 0.8;
    }
  }
  
  @media (max-width: 768px) {
    width: 100%;
    position: relative;
  }
}

.mobile-menu-button {
  position: fixed;
  top: 16px;
  left: 16px;
  z-index: 999;
  width: 40px;
  height: 40px;
  background-color: var(--border-color-hover);
  border: none;
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  
  &:hover {
    background-color: var(--bg-hover);
  }
  
  @media (min-width: 769px) {
    display: none;
  }
}
</style>
