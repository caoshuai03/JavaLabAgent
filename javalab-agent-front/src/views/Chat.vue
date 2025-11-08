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
  // 初始化对话 store，从 localStorage 加载数据
  chatStore.initialize()
  
  // 移动端默认折叠侧边栏
  if (window.innerWidth <= 768) {
    chatStore.sidebarCollapsed = true
  }
  
  // 如果没有当前对话ID，创建一个新对话（但不添加到列表，直到用户发送第一条消息）
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
