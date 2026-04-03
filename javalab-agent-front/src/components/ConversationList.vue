<template>
  <div class="conversation-list">
    <div class="list-container">
      <ConversationItem
        v-for="conversation in chatStore.conversations"
        :key="conversation.id"
        :conversation="conversation"
        :is-active="route.path === '/' && conversation.id === chatStore.currentConversationId"
        :is-selection-mode="isSelectionMode"
        :is-selected="selectedIds.includes(conversation.id)"
        :is-menu-open="openedMenuConversationId === conversation.id"
        @select="handleSelect"
        @delete="handleDelete"
        @rename="handleRename"
        @toggleSelect="handleToggleSelect"
        @enterBatchMode="handleEnterBatchMode"
        @toggleMenu="handleToggleMenu"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useChatStore } from '../stores/chat'
import ConversationItem from './ConversationItem.vue'

const props = defineProps({
  isSelectionMode: {
    type: Boolean,
    default: false,
  },
  selectedIds: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:selectedIds', 'enterBatchMode'])

const router = useRouter()
const route = useRoute()
const chatStore = useChatStore()
const openedMenuConversationId = ref(null)

const handleSelect = (conversationId) => {
  openedMenuConversationId.value = null
  chatStore.switchConversation(conversationId)
  // 如果当前不在聊天页面，导航回聊天页面
  if (route.path !== '/') {
    router.push('/')
  }
}

const handleDelete = (conversationId) => {
  openedMenuConversationId.value = null
  if (confirm('确定要删除这个对话吗？')) {
    chatStore.deleteConversation(conversationId)
  }
}

const handleRename = (conversationId, newTitle) => {
  chatStore.renameConversation(conversationId, newTitle)
}

// 统一维护当前展开的会话菜单
const handleToggleMenu = ({ conversationId, nextOpen }) => {
  openedMenuConversationId.value = nextOpen ? conversationId : null
}

const handleToggleSelect = (conversationId) => {
  const newSelectedIds = [...props.selectedIds]
  const index = newSelectedIds.indexOf(conversationId)
  if (index === -1) {
    newSelectedIds.push(conversationId)
  } else {
    newSelectedIds.splice(index, 1)
  }
  emit('update:selectedIds', newSelectedIds)
}

// 进入批量管理模式
const handleEnterBatchMode = () => {
  openedMenuConversationId.value = null
  // 从会话项菜单进入批量模式，同时选中当前会话
  const newSelectedIds = [...props.selectedIds]
  // 不需要自动选中，让用户自己选择
  emit('enterBatchMode')
}
</script>

<style lang="scss" scoped>
.conversation-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--scrollbar-thumb-light, rgba(0, 0, 0, 0.15));
    border-radius: 4px;

    &:hover {
      background: var(--scrollbar-thumb-hover-light, rgba(0, 0, 0, 0.25));
    }
  }

  .list-container {
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
