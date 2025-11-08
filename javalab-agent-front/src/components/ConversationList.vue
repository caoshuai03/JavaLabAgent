<template>
  <div class="conversation-list">
    <div class="list-container">
      <ConversationItem
        v-for="conversation in chatStore.conversations"
        :key="conversation.id"
        :conversation="conversation"
        :is-active="conversation.id === chatStore.currentConversationId"
        @select="handleSelect"
        @delete="handleDelete"
        @rename="handleRename"
      />
    </div>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useChatStore } from '../stores/chat'
import ConversationItem from './ConversationItem.vue'

const router = useRouter()
const route = useRoute()
const chatStore = useChatStore()

const handleSelect = (conversationId) => {
  chatStore.switchConversation(conversationId)
  // 如果当前在知识库页面，导航回聊天页面
  if (route.path === '/knowledge') {
    router.push('/')
  }
}

const handleDelete = (conversationId) => {
  if (confirm('确定要删除这个对话吗？')) {
    chatStore.deleteConversation(conversationId)
  }
}

const handleRename = (conversationId, newTitle) => {
  chatStore.renameConversation(conversationId, newTitle)
}
</script>

<style lang="scss" scoped>
.conversation-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  
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
  
  .list-container {
    padding: 8px;
  }
}
</style>

