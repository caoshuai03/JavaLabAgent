<template>
  <div :class="['sidebar', { collapsed: chatStore.sidebarCollapsed }]">
    <div class="sidebar-header">
      <div class="sidebar-top">
        <div class="logo-area" v-if="!chatStore.sidebarCollapsed" @click="handleNewConversation">
          <img src="../assets/logo.png" alt="JavaLab Logo" class="logo-img" />
        </div>
        <button
          @click="chatStore.toggleSidebar"
          class="toggle-button"
          v-tooltip="chatStore.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        >
          <ChevronLeftIcon v-if="!chatStore.sidebarCollapsed" :size="16" />
          <ChevronRightIcon v-else :size="16" />
        </button>
      </div>
      <div class="nav-menu">
        <button
          @click="handleNewConversation"
          :class="['nav-item', { active: isNavActive('/') }]"
          v-tooltip="chatStore.sidebarCollapsed ? '新建对话' : ''"
        >
          <PlusIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">新聊天</span>
        </button>

        <button
          @click="handleKnowledgeManagement"
          :class="['nav-item', { active: isNavActive('/knowledge') }]"
          v-tooltip="chatStore.sidebarCollapsed ? '知识库' : ''"
        >
          <FolderIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">知识库</span>
        </button>

        <button
          @click="handleMcpSettings"
          :class="['nav-item', { active: isNavActive('/mcp') }]"
          v-tooltip="chatStore.sidebarCollapsed ? 'MCP' : ''"
        >
          <ToolIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">MCP</span>
        </button>

        <button
          @click="handleSkillsManagement"
          :class="['nav-item', { active: isNavActive('/skills') }]"
          v-tooltip="chatStore.sidebarCollapsed ? 'Skills' : ''"
        >
          <BookIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">Skills</span>
        </button>
      </div>
    </div>

    <div class="list-header" v-if="!chatStore.sidebarCollapsed">
      <span class="title">历史会话</span>
    </div>

    <ConversationList
      v-if="!chatStore.sidebarCollapsed"
      :is-selection-mode="isSelectionMode"
      :selected-ids="selectedIds"
      @update:selected-ids="(val) => (selectedIds = val)"
      @enterBatchMode="handleEnterBatchModeFromItem"
    />

    <div class="sidebar-bottom" v-if="isSelectionMode && !chatStore.sidebarCollapsed">
      <div class="batch-actions">
        <button class="batch-btn cancel" @click="cancelSelectionMode">取消</button>
        <button
          class="batch-btn delete"
          @click="handleBatchDelete"
          :disabled="selectedIds.length === 0"
        >
          删除 ({{ selectedIds.length }})
        </button>
      </div>
    </div>

    <div class="sidebar-bottom" v-else>
      <UserProfile />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useChatStore } from '../stores/chat'
import ConversationList from './ConversationList.vue'
import UserProfile from './UserProfile.vue'
import PlusIcon from './icons/PlusIcon.vue'
import FolderIcon from './icons/FolderIcon.vue'
import ToolIcon from './icons/ToolIcon.vue'
import BookIcon from './icons/BookIcon.vue'

import ChevronLeftIcon from './icons/ChevronLeftIcon.vue'
import ChevronRightIcon from './icons/ChevronRightIcon.vue'

const router = useRouter()
const route = useRoute()
const chatStore = useChatStore()

const isSelectionMode = ref(false)
const selectedIds = ref([])

const cancelSelectionMode = () => {
  isSelectionMode.value = false
  selectedIds.value = []
}

// 从会话项菜单进入批量模式
const handleEnterBatchModeFromItem = () => {
  isSelectionMode.value = true
  // 不自动选择任何会话，让用户自己选择
}

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return

  if (confirm(`确定要删除选中的 ${selectedIds.value.length} 个对话吗？`)) {
    const success = await chatStore.deleteConversations(selectedIds.value)
    if (success) {
      isSelectionMode.value = false
      selectedIds.value = []
    }
  }
}

/**
 * 创建新对话
 * 新对话的 sessionId 由后端在第一次发送消息时生成
 */
const handleNewConversation = () => {
  // 创建新对话（此时不会生成ID，等待后端返回）
  chatStore.createConversation()

  // 如果当前不在聊天页面，导航回聊天界面
  if (route.path !== '/') {
    router.push('/')
  }
}

const handleKnowledgeManagement = () => {
  router.push('/knowledge')
}

const handleMcpSettings = () => {
  router.push('/mcp')
}

const handleSkillsManagement = () => {
  router.push('/skills')
}

// 判断当前路由是否与某个导航项匹配，便于给选中的入口加背景高亮
// “新聊天”只在新对话状态下高亮，避免和历史会话的选中状态互相覆盖
const isNavActive = (path) => {
  if (path === '/') {
    return route.path === '/' && chatStore.isNewConversation
  }

  return route.path === path
}
</script>

<style lang="scss" scoped>
.sidebar {
  width: 260px;
  height: 100vh;
  background-color: var(--bg-secondary);
  display: flex;
  flex-direction: column;
  transition:
    width 0.3s ease,
    background-color 0.3s ease;
  border-right: none;
  flex-shrink: 0;
  position: relative;

  &.collapsed {
    width: 0;
    overflow: visible;
    background-color: transparent;
    border-right: none;
  }

  // 移动端响应式
  @media (max-width: 768px) {
    position: fixed;
    left: 0;
    top: 0;
    z-index: 1000;
    transform: translateX(0);
    transition:
      transform 0.3s ease,
      background-color 0.3s ease,
      width 0.3s ease;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.3);

    &.collapsed {
      transform: translateX(-260px);
      width: 0;
      overflow: hidden;
    }
  }

  // 平板响应式
  @media (min-width: 769px) and (max-width: 1024px) {
    width: 220px;

    &.collapsed {
      width: 0;
      overflow: hidden;
    }
  }
}

.sidebar-header {
  display: flex;
  flex-direction: column;
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
    gap: 6px;

    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      background-color: transparent;
      border: none;
      border-radius: 12px;
      color: var(--text-primary);
      cursor: pointer;
      font-size: 14px;
      text-align: left;
      transition: all 0.2s ease;
      min-height: 40px;

      &:hover {
        background-color: var(--nav-bg-hover);
      }

      &.active {
        background-color: var(--nav-bg-active);
        color: var(--primary-color);
      }

      &.active:hover {
        background-color: var(--nav-bg-active);
      }

      &:focus {
        outline: none;
      }

      &:focus-visible {
        outline: none;
        background-color: var(--nav-bg-hover);
      }

      /* 统一侧边栏图标的描边粗细与线帽样式，避免不同图标看起来不一致 */
      svg {
        flex-shrink: 0;
        width: 18px;
        height: 18px;
        stroke: currentColor;
        stroke-width: 1.85;
        stroke-linecap: round;
        stroke-linejoin: round;
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
    position: fixed;
    left: 0;
    top: 0;
    z-index: 1001;

    .sidebar-top {
      padding: 12px;

      .logo-area {
        display: none;
      }

      .toggle-button {
        width: 32px;
        height: 32px;
        background-color: transparent;
        border: 1px solid transparent;
        border-radius: 6px;
        transition: none;
      }
    }

    .nav-menu {
      display: none;
    }
  }
}

.sidebar-bottom {
  margin-top: auto;
  display: flex;
  flex-direction: column;

  .sidebar.collapsed & {
    display: none;
  }
}

.list-header {
  display: flex;
  align-items: center;
  padding: 12px 16px 8px;

  .title {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
}

.batch-actions {
  display: flex;
  padding: 12px;
  gap: 12px;

  .batch-btn {
    flex: 1;
    padding: 8px;
    border-radius: 20px;
    font-size: 13px;
    cursor: pointer;
    border: none;
    transition: all 0.2s;

    &.cancel {
      background-color: var(--bg-primary);
      color: var(--text-primary);

      &:hover {
        background-color: var(--bg-hover);
      }
    }

    &.delete {
      background-color: rgba(220, 53, 69, 0.1);
      color: #dc3545;

      &:hover:not(:disabled) {
        background-color: rgba(220, 53, 69, 0.2);
      }

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  }
}
</style>
