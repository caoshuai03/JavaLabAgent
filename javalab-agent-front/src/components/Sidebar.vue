<template>
  <div :class="['sidebar', { collapsed: chatStore.sidebarCollapsed }]">
    <div class="sidebar-header">
      <div class="sidebar-top">
        <div class="logo-area" v-if="!chatStore.sidebarCollapsed" @click="handleNewConversation" title="新聊天">
          <img src="../assets/logo.png" alt="JavaLab Logo" class="logo-img" />
        </div>
        <button
          @click="chatStore.toggleSidebar"
          class="toggle-button"
          :title="chatStore.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        >
          <ChevronLeftIcon v-if="!chatStore.sidebarCollapsed" :size="16" />
          <ChevronRightIcon v-else :size="16" />
        </button>
      </div>
      <div class="nav-menu">
        <button
          @click="handleNewConversation"
          class="nav-item"
          :title="chatStore.sidebarCollapsed ? '新建对话' : ''"
        >
          <PlusIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">新聊天</span>
        </button>

        <button
          @click="handleKnowledgeManagement"
          class="nav-item"
          :title="chatStore.sidebarCollapsed ? '知识库' : ''"
        >
          <FolderIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">知识库</span>
        </button>
      </div>
    </div>

    <ConversationList v-if="!chatStore.sidebarCollapsed" />

    <div class="sidebar-bottom">
      <UserProfile />
    </div>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useChatStore } from '../stores/chat'
import ConversationList from './ConversationList.vue'
import UserProfile from './UserProfile.vue'
import PlusIcon from './icons/PlusIcon.vue'
import FolderIcon from './icons/FolderIcon.vue'

import ChevronLeftIcon from './icons/ChevronLeftIcon.vue'
import ChevronRightIcon from './icons/ChevronRightIcon.vue'

const router = useRouter()
const route = useRoute()
const chatStore = useChatStore()

/**
 * 创建新对话
 * 新对话的 sessionId 由后端在第一次发送消息时生成
 */
const handleNewConversation = () => {
  // 创建新对话（此时不会生成ID，等待后端返回）
  chatStore.createConversation()

  // 如果当前在知识库页面，导航回对话界面
  if (route.path === '/knowledge') {
    router.push('/')
  }
}

const handleKnowledgeManagement = () => {
  router.push('/knowledge')
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
  position: relative;

  &.collapsed {
    width: 64px;

    // 确保折叠状态下内容居中
    .sidebar-header {
      align-items: center;
    }
  }

  // 移动端响应式
  @media (max-width: 768px) {
    position: fixed;
    left: 0;
    top: 0;
    z-index: 1000;
    transform: translateX(0);
    transition: transform 0.3s ease, background-color 0.3s ease, width 0.3s ease;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.3);

    &.collapsed {
      transform: translateX(-100%);
      width: 260px; // 移动端折叠时完全隐藏，保持原始宽度
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
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--border-color);
  transition: all 0.3s ease;

  .sidebar-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 12px 12px 12px;

    .logo-area {
      flex: 1;
      cursor: pointer;
      display: flex;
      align-items: center;

      .logo-img {
        height: 32px;
        width: auto;
        object-fit: contain;
      }
    }

    .toggle-button {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      padding: 0;
      background-color: transparent;
      border: 1px solid transparent;
      border-radius: 6px;
      color: var(--text-primary);
      cursor: pointer;
      transition: all 0.2s ease;
      flex-shrink: 0;

      &:hover {
        background-color: var(--bg-hover);
      }

      &:focus {
        outline: none;
      }

      svg {
        flex-shrink: 0;
      }
    }
  }

  .nav-menu {
    display: flex;
    flex-direction: column;
    padding: 0 8px 8px 8px;
    gap: 0;

    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      background-color: transparent;
      border: none;
      border-radius: 6px;
      color: var(--text-primary);
      cursor: pointer;
      font-size: 14px;
      text-align: left;
      transition: all 0.2s ease;
      min-height: 40px;

      &:hover {
        background-color: var(--bg-hover);
      }

      &:focus {
        outline: none;
      }

      &:focus-visible {
        outline: none;
        background-color: var(--bg-hover);
      }

      svg {
        flex-shrink: 0;
        stroke: currentColor;
      }

      span {
        flex: 1;
        white-space: nowrap;
      }
    }

  }
}

.sidebar.collapsed {
  .sidebar-header {
    .sidebar-top {
      padding: 16px 8px 12px 8px;
      justify-content: center;

      .logo-area {
        display: none;
      }

      .toggle-button {
        width: 40px;
        height: 40px;
      }
    }

    .nav-menu {
      align-items: center;
      padding: 0 8px 8px 8px;

      .nav-item {
        justify-content: center;
        width: 40px;
        padding: 10px;

        span {
          display: none;
        }
      }
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
