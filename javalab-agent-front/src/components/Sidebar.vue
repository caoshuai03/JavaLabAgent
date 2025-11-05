<template>
  <div :class="['sidebar', { collapsed: chatStore.sidebarCollapsed }]">
    <div class="sidebar-header">
      <button 
        v-if="!chatStore.sidebarCollapsed" 
        @click="handleNewConversation" 
        class="new-chat-button"
      >
        <span class="icon">+</span>
        <span>新建对话</span>
      </button>
      <button 
        v-else 
        @click="handleNewConversation" 
        class="new-chat-button collapsed"
        title="新建对话"
      >
        <span class="icon">+</span>
      </button>
      <button 
        @click="chatStore.toggleSidebar" 
        class="toggle-button"
        :title="chatStore.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
      >
        <span class="icon">{{ chatStore.sidebarCollapsed ? '→' : '←' }}</span>
      </button>
    </div>
    
    <ConversationList v-if="!chatStore.sidebarCollapsed" />
    
    <div class="sidebar-footer">
      <button 
        @click="themeStore.toggleTheme" 
        class="theme-toggle-button"
        :title="themeStore.theme === 'light' ? '切换到深色模式' : '切换到浅色模式'"
      >
        <span class="icon">{{ themeStore.theme === 'light' ? '🌙' : '☀️' }}</span>
        <span v-if="!chatStore.sidebarCollapsed" class="text">
          {{ themeStore.theme === 'light' ? '深色模式' : '浅色模式' }}
        </span>
      </button>
    </div>
    
    <UserProfile />
  </div>
</template>

<script setup>
import { useChatStore } from '../stores/chat'
import { useThemeStore } from '../stores/theme'
import ConversationList from './ConversationList.vue'
import UserProfile from './UserProfile.vue'

const chatStore = useChatStore()
const themeStore = useThemeStore()

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
    
    .icon {
      font-size: 18px;
      font-weight: bold;
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
    
    .icon {
      font-size: 16px;
    }
  }
}

.sidebar-footer {
  padding: 8px;
  border-top: 1px solid var(--border-color);
  transition: padding 0.3s ease;
  
  .sidebar.collapsed & {
    padding: 8px;
  }
  
  .theme-toggle-button {
    width: 100%;
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
    
    .sidebar.collapsed & {
      width: 48px;
      padding: 12px;
      margin: 0 auto;
    }
    
    &:hover {
      background-color: var(--bg-hover);
      border-color: var(--text-primary);
    }
    
    .icon {
      font-size: 18px;
      flex-shrink: 0;
    }
    
    .text {
      white-space: nowrap;
      transition: opacity 0.3s ease, width 0.3s ease;
      
      .sidebar.collapsed & {
        display: none;
      }
      
      @media (max-width: 768px) {
        display: none;
      }
    }
  }
}
</style>

