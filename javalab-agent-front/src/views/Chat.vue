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

/**
 * 初始化聊天页面
 * 从数据库加载会话列表和历史消息
 */
onMounted(async () => {
  // 移动端默认折叠侧边栏
  if (window.innerWidth <= 768) {
    chatStore.sidebarCollapsed = true
  }
  
  // 从数据库加载会话（异步操作）
  await chatStore.initialize()
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
