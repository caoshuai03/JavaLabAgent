<template>
  <div class="chat-container">
    <Sidebar />
    <ChatMain />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useChatStore } from '../stores/chat'
import Sidebar from '../components/Sidebar.vue'
import ChatMain from '../components/ChatMain.vue'

const chatStore = useChatStore()

onMounted(() => {
  chatStore.initialize()
  
  if (window.innerWidth <= 768) {
    chatStore.sidebarCollapsed = true
  }
  
  if (!chatStore.currentConversationId) {
    chatStore.createConversation()
  }
})
</script>

<style lang="scss" scoped>
.chat-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: var(--bg-primary);
  transition: background-color 0.3s ease;
}
</style>
