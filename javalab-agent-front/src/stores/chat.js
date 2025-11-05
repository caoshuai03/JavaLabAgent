import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // 对话列表
  const conversations = ref([])
  
  // 当前对话ID
  const currentConversationId = ref(null)
  
  // 当前对话的消息列表
  const messages = ref([])
  
  // 加载状态
  const isLoading = ref(false)
  
  // 流式响应状态
  const isStreaming = ref(false)
  
  // 侧边栏折叠状态
  const sidebarCollapsed = ref(false)
  
  // 当前对话
  const currentConversation = computed(() => {
    return conversations.value.find(conv => conv.id === currentConversationId.value)
  })
  
  // 生成新的对话ID
  const generateConversationId = () => {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }
  
  // 生成新的消息ID
  const generateMessageId = () => {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }
  
  // 新建对话
  const createConversation = () => {
    const newId = generateConversationId()
    const newConversation = {
      id: newId,
      title: '新对话',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
    
    conversations.value.unshift(newConversation)
    currentConversationId.value = newId
    messages.value = []
    
    // 保存到 localStorage
    saveConversationsToStorage()
    
    return newId
  }
  
  // 切换对话
  const switchConversation = (conversationId) => {
    // 保存当前对话的消息
    if (currentConversationId.value) {
      saveCurrentConversationMessages()
    }
    
    // 切换对话
    currentConversationId.value = conversationId
    const conversation = conversations.value.find(conv => conv.id === conversationId)
    
    if (conversation) {
      // 从 localStorage 加载消息
      loadConversationMessages(conversationId)
      // 更新最后访问时间
      conversation.updatedAt = new Date().toISOString()
      saveConversationsToStorage()
    } else {
      messages.value = []
    }
  }
  
  // 删除对话
  const deleteConversation = (conversationId) => {
    const index = conversations.value.findIndex(conv => conv.id === conversationId)
    if (index !== -1) {
      conversations.value.splice(index, 1)
      
      // 如果删除的是当前对话，切换到最新对话或创建新对话
      if (conversationId === currentConversationId.value) {
        if (conversations.value.length > 0) {
          switchConversation(conversations.value[0].id)
        } else {
          currentConversationId.value = null
          messages.value = []
        }
      }
      
      // 从 localStorage 删除消息
      localStorage.removeItem(`chat_messages_${conversationId}`)
      saveConversationsToStorage()
    }
  }
  
  // 重命名对话
  const renameConversation = (conversationId, newTitle) => {
    const conversation = conversations.value.find(conv => conv.id === conversationId)
    if (conversation) {
      conversation.title = newTitle || '新对话'
      conversation.updatedAt = new Date().toISOString()
      saveConversationsToStorage()
    }
  }
  
  // 更新对话标题（基于首条消息）
  const updateConversationTitle = (conversationId, firstMessage) => {
    const conversation = conversations.value.find(conv => conv.id === conversationId)
    if (conversation && conversation.title === '新对话') {
      // 使用首条消息的前30个字符作为标题
      const title = firstMessage.length > 30 
        ? firstMessage.substring(0, 30) + '...' 
        : firstMessage
      conversation.title = title
      conversation.updatedAt = new Date().toISOString()
      saveConversationsToStorage()
    }
  }
  
  // 添加消息
  const addMessage = (sender, content, conversationId = null) => {
    const convId = conversationId || currentConversationId.value
    
    // 如果没有对话，创建新对话
    if (!convId) {
      const newId = createConversation()
      conversationId = newId
    }
    
    const message = {
      id: generateMessageId(),
      sender: sender, // 'user' 或 'assistant'
      content: content,
      timestamp: new Date().toISOString()
    }
    
    messages.value.push(message)
    
    // 如果是用户的第一条消息，更新对话标题
    if (sender === 'user' && messages.value.filter(m => m.sender === 'user').length === 1) {
      updateConversationTitle(convId, content)
    }
    
    // 保存消息到 localStorage
    saveCurrentConversationMessages()
    
    return message
  }
  
  // 更新最后一条消息（用于流式响应）
  const updateLastMessage = (content) => {
    if (messages.value.length > 0) {
      const lastMessage = messages.value[messages.value.length - 1]
      if (lastMessage.sender === 'assistant') {
        lastMessage.content = content
        // 实时保存（节流）
        debounceSaveMessages()
      }
    }
  }
  
  // 保存当前对话的消息到 localStorage
  const saveCurrentConversationMessages = () => {
    if (currentConversationId.value) {
      localStorage.setItem(
        `chat_messages_${currentConversationId.value}`,
        JSON.stringify(messages.value)
      )
    }
  }
  
  // 从 localStorage 加载对话消息
  const loadConversationMessages = (conversationId) => {
    const stored = localStorage.getItem(`chat_messages_${conversationId}`)
    if (stored) {
      try {
        messages.value = JSON.parse(stored)
      } catch (e) {
        console.error('Failed to load messages:', e)
        messages.value = []
      }
    } else {
      messages.value = []
    }
  }
  
  // 保存对话列表到 localStorage
  const saveConversationsToStorage = () => {
    localStorage.setItem('chat_conversations', JSON.stringify(conversations.value))
    if (currentConversationId.value) {
      localStorage.setItem('chat_current_conversation_id', currentConversationId.value)
    }
  }
  
  // 从 localStorage 加载对话列表
  const loadConversationsFromStorage = () => {
    const stored = localStorage.getItem('chat_conversations')
    if (stored) {
      try {
        conversations.value = JSON.parse(stored)
      } catch (e) {
        console.error('Failed to load conversations:', e)
        conversations.value = []
      }
    }
    
    const currentId = localStorage.getItem('chat_current_conversation_id')
    if (currentId && conversations.value.find(conv => conv.id === currentId)) {
      currentConversationId.value = currentId
      loadConversationMessages(currentId)
    }
  }
  
  // 清空当前对话的消息
  const clearMessages = () => {
    messages.value = []
    if (currentConversationId.value) {
      saveCurrentConversationMessages()
    }
  }
  
  // 防抖保存消息
  let saveTimer = null
  const debounceSaveMessages = () => {
    if (saveTimer) {
      clearTimeout(saveTimer)
    }
    saveTimer = setTimeout(() => {
      saveCurrentConversationMessages()
    }, 1000) // 1秒后保存
  }
  
  // 切换侧边栏
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
  
  // 初始化：从 localStorage 加载数据
  const initialize = () => {
    loadConversationsFromStorage()
  }
  
  return {
    // 状态
    conversations,
    currentConversationId,
    messages,
    isLoading,
    isStreaming,
    sidebarCollapsed,
    
    // 计算属性
    currentConversation,
    
    // 方法
    createConversation,
    switchConversation,
    deleteConversation,
    renameConversation,
    updateConversationTitle,
    addMessage,
    updateLastMessage,
    clearMessages,
    toggleSidebar,
    initialize,
    saveCurrentConversationMessages
  }
})

