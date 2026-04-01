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

        <button
          @click="handleMcpSettings"
          class="nav-item"
          :title="chatStore.sidebarCollapsed ? 'MCP' : ''"
        >
          <ToolIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">MCP</span>
        </button>

        <button
          @click="handleSkillsManagement"
          class="nav-item"
          :title="chatStore.sidebarCollapsed ? 'Skills' : ''"
        >
          <BookIcon :size="18" />
          <span v-if="!chatStore.sidebarCollapsed">Skills</span>
        </button>
      </div>
    </div>

    <div class="list-header" v-if="!chatStore.sidebarCollapsed">
      <span class="title">历史会话</span>
      <button class="edit-btn" @click="toggleSelectionMode" :title="isSelectionMode ? '完成' : '批量编辑'">
        <span v-if="isSelectionMode" class="text-btn">完成</span>
        <EditIcon v-else :size="14" />
      </button>
    </div>

    <ConversationList
      v-if="!chatStore.sidebarCollapsed"
      :is-selection-mode="isSelectionMode"
      :selected-ids="selectedIds"
      @update:selected-ids="val => selectedIds = val"
    />

    <div class="sidebar-bottom" v-if="isSelectionMode && !chatStore.sidebarCollapsed">
      <div class="batch-actions">
        <button
          class="batch-btn cancel"
          @click="cancelSelectionMode"
        >
          取消
        </button>
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
import EditIcon from './icons/EditIcon.vue'

import ChevronLeftIcon from './icons/ChevronLeftIcon.vue'
import ChevronRightIcon from './icons/ChevronRightIcon.vue'

const router = useRouter()
const route = useRoute()
const chatStore = useChatStore()

const isSelectionMode = ref(false)
const selectedIds = ref([])

const toggleSelectionMode = () => {
  isSelectionMode.value = !isSelectionMode.value
  if (!isSelectionMode.value) {
    selectedIds.value = []
  }
}

const cancelSelectionMode = () => {
  isSelectionMode.value = false
  selectedIds.value = []
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

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 8px;

  .title {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .edit-btn {
    background: none;
    border: none;
    cursor: pointer;
    color: var(--text-secondary);
    padding: 4px;
    border-radius: 4px;
    display: flex;
    align-items: center;
    font-size: 12px;

    &:hover {
      background-color: var(--bg-hover);
      color: var(--text-primary);
    }

    .text-btn {
      color: var(--primary-color);
      font-weight: 500;
    }
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
