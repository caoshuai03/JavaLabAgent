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
import { useChatStore } from '../stores/chat'
import ConversationItem from './ConversationItem.vue'

const chatStore = useChatStore()

const handleSelect = (conversationId) => {
  chatStore.switchConversation(conversationId)
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

