<template>
  <div :class="['sidebar', { collapsed: chatStore.sidebarCollapsed }]">
    <div class="sidebar-header">
      <button 
        v-if="!chatStore.sidebarCollapsed" 
        @click="handleNewConversation" 
        class="new-chat-button"
      >
        <PlusIcon :size="18" />
        <span>新建对话</span>
      </button>
      <button 
        v-else 
        @click="handleNewConversation" 
        class="new-chat-button collapsed"
        title="新建对话"
      >
        <PlusIcon :size="18" />
      </button>
      <button 
        @click="chatStore.toggleSidebar" 
        class="toggle-button"
        :title="chatStore.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
      >
        <ChevronLeftIcon v-if="!chatStore.sidebarCollapsed" :size="16" />
        <ChevronRightIcon v-else :size="16" />
      </button>
    </div>
    
    <ConversationList v-if="!chatStore.sidebarCollapsed" />
    
    <div class="sidebar-bottom">
      <UserProfile />
    </div>
  </div>
</template>

<script setup>
import { useChatStore } from '../stores/chat'
import ConversationList from './ConversationList.vue'
import UserProfile from './UserProfile.vue'
import PlusIcon from './icons/PlusIcon.vue'
import ChevronLeftIcon from './icons/ChevronLeftIcon.vue'
import ChevronRightIcon from './icons/ChevronRightIcon.vue'

const chatStore = useChatStore()

const handleNewConversation = () => {
  chatStore.createConversation()
}
</script>

<style lang="scss" scoped>
.sidebar {
  width: 260px;
  height: 100vh;
  background-color: var(--bg-secondary);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, background-color 0.3s ease;
  border-right: 1px solid var(--border-color);
  flex-shrink: 0;
  
  &.collapsed {
    width: 64px;
  }
  
  // 移动端响应式
  @media (max-width: 768px) {
    position: fixed;
    left: 0;
    top: 0;
    z-index: 1000;
    transform: translateX(0);
    transition: transform 0.3s ease, background-color 0.3s ease;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.3);
    
    &.collapsed {
      transform: translateX(-100%);
    }
  }
  
  // 平板响应式
  @media (min-width: 769px) and (max-width: 1024px) {
    width: 220px;
    
    &.collapsed {
      width: 64px;
    }
  }
}

.sidebar-header {
  padding: 8px;
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border-color);
  transition: padding 0.3s ease;
  
  .sidebar.collapsed & {
    flex-direction: column;
    align-items: center;
    padding: 8px;
    gap: 8px;
  }
  
  .new-chat-button {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 12px;
    background-color: transparent;
    border: 1px solid var(--border-color-hover);
    border-radius: 8px;
    color: var(--text-primary);
    cursor: pointer;
    font-size: 14px;
    transition: all 0.3s ease;
    min-width: 0;
    
    &:hover {
      background-color: var(--bg-hover);
      border-color: var(--text-primary);
    }
    
    &.collapsed {
      flex: 0;
      width: 48px;
      padding: 12px;
      
      .icon {
        margin: 0;
      }
    }
    
    svg {
      flex-shrink: 0;
    }
  }
  
  .toggle-button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    padding: 12px;
    background-color: transparent;
    border: 1px solid var(--border-color-hover);
    border-radius: 8px;
    color: var(--text-primary);
    cursor: pointer;
    transition: all 0.3s ease;
    flex-shrink: 0;
    
    .sidebar.collapsed & {
      width: 48px;
    }
    
    &:hover {
      background-color: var(--bg-hover);
      border-color: var(--text-primary);
    }
    
    svg {
      flex-shrink: 0;
    }
  }
}

.sidebar-bottom {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--border-color);
  
  .sidebar.collapsed & {
    align-items: center;
    justify-content: center;
  }
}
</style>

