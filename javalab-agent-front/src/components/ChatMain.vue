<template>
  <div class="chat-main" @click="handleMainClick">
    <button 
      v-if="showMobileMenuButton" 
      @click.stop="toggleSidebar" 
      class="mobile-menu-button"
      title="打开菜单"
    >
      ☰
    </button>
    <MessageList />
    <ChatInput />
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
  // 移动端默认折叠侧边栏
  if (isMobile.value && !chatStore.sidebarCollapsed) {
    chatStore.sidebarCollapsed = true
  }
}

const handleMainClick = () => {
  // 移动端点击主区域时关闭侧边栏
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
  min-width: 0; // 防止 flex 子元素溢出
  transition: background-color 0.3s ease;
  
  // 移动端响应式
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

