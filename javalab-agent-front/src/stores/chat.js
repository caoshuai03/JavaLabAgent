import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserSessions, getSessionHistory } from '../api/chat'
import { useUserStore } from './user'

export const useChatStore = defineStore('chat', () => {
  // 会话列表
  const conversations = ref([])

  // 当前会话ID（对应后端的 sessionId）
  const currentConversationId = ref(null)

  // 当前会话的消息列表
  const messages = ref([])

  // 加载状态
  const isLoading = ref(false)

  // 流式响应状态
  const isStreaming = ref(false)

  // 侧边栏折叠状态
  const sidebarCollapsed = ref(false)

  // 是否需要聚焦输入框
  const shouldFocusInput = ref(false)

  // 是否是新对话（尚未发送消息）
  const isNewConversation = ref(false)

  const currentConversation = computed(() => {
    return conversations.value.find(conv => conv.id === currentConversationId.value)
  })

  const generateConversationId = () => {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  const generateMessageId = () => {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  /**
   * 创建新对话
   * 注意：新对话的 ID 由后端在第一次发送消息时生成
   */
  const createConversation = () => {
    // 标记为新对话，不设置 ID（等待后端返回）
    currentConversationId.value = null
    messages.value = []
    isNewConversation.value = true

    // 清除 localStorage 中的当前会话ID
    localStorage.removeItem('chat_current_conversation_id')

    focusInput()

    return null
  }

  /**
   * 设置当前会话ID（由后端返回的 sessionId）
   * @param {string} sessionId - 后端返回的会话ID
   */
  const setCurrentSessionId = (sessionId) => {
    currentConversationId.value = sessionId
    isNewConversation.value = false
    localStorage.setItem('chat_current_conversation_id', sessionId)
  }


  /**
   * 切换到指定会话，从数据库加载历史消息
   * @param {string} conversationId - 会话ID
   */
  const switchConversation = async (conversationId) => {
    currentConversationId.value = conversationId
    isNewConversation.value = false
    localStorage.setItem('chat_current_conversation_id', conversationId)

    const conversation = conversations.value.find(conv => conv.id === conversationId)

    if (conversation) {
      // 从数据库加载历史消息
      await loadConversationMessagesFromDB(conversationId)
    } else {
      messages.value = []
    }

    // 触发输入框聚焦
    focusInput()
  }

  /**
   * 删除对话
   * TODO: 后续可以添加后端删除接口
   * @param {string} conversationId - 会话ID
   */
  const deleteConversation = (conversationId) => {
    const index = conversations.value.findIndex(conv => conv.id === conversationId)
    if (index !== -1) {
      conversations.value.splice(index, 1)

      if (conversationId === currentConversationId.value) {
        if (conversations.value.length > 0) {
          switchConversation(conversations.value[0].id)
        } else {
          currentConversationId.value = null
          messages.value = []
          isNewConversation.value = true
        }
      }
    }
  }

  /**
   * 重命名会话
   * TODO: 后续可以添加后端更新接口
   */
  const renameConversation = (conversationId, newTitle) => {
    const conversation = conversations.value.find(conv => conv.id === conversationId)
    if (conversation) {
      conversation.title = newTitle || '新对话'
      conversation.updatedAt = new Date().toISOString()
    }
  }

  /**
   * 更新会话标题（根据第一条消息）
   */
  const updateConversationTitle = (conversationId, firstMessage) => {
    const conversation = conversations.value.find(conv => conv.id === conversationId)
    if (conversation && conversation.title === '新对话') {
      const title = firstMessage.length > 30
        ? firstMessage.substring(0, 30) + '...'
        : firstMessage
      conversation.title = title
      conversation.updatedAt = new Date().toISOString()
    }
  }

  /**
   * 添加消息到当前会话
   * @param {string} sender - 发送者类型（'user' 或 'assistant'）
   * @param {string} content - 消息内容
   * @returns {object} 消息对象
   */
  const addMessage = (sender, content) => {
    const message = {
      id: generateMessageId(),
      sender: sender,
      content: content,
      timestamp: new Date().toISOString()
    }

    messages.value.push(message)

    return message
  }

  /**
   * 将当前新对话添加到会话列表
   * @param {string} sessionId - 后端返回的会话ID
   * @param {string} title - 会话标题（通常是第一条消息）
   */
  const addNewConversationToList = (sessionId, title) => {
    const newConversation = {
      id: sessionId,
      title: title.length > 30 ? title.substring(0, 30) + '...' : title,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }

    // 添加到列表顶部
    conversations.value.unshift(newConversation)
  }

  /**
   * 更新最后一条消息内容（用于流式响应）
   */
  const updateLastMessage = (content) => {
    if (messages.value.length > 0) {
      const lastMessage = messages.value[messages.value.length - 1]
      if (lastMessage.sender === 'assistant') {
        lastMessage.content = content
      }
    }
  }

  /**
   * 从数据库加载会话的历史消息
   * @param {string} sessionId - 会话ID
   */
  const loadConversationMessagesFromDB = async (sessionId) => {
    try {
      const response = await getSessionHistory(sessionId)
      const dbMessages = response.data || []
      
      // 转换后端消息格式为前端格式
      messages.value = dbMessages.map(msg => ({
        id: msg.id || generateMessageId(),
        sender: msg.role === 'user' ? 'user' : 'assistant',
        content: msg.content,
        timestamp: msg.createTime || new Date().toISOString()
      }))
    } catch (error) {
      console.error('加载会话历史失败:', error)
      messages.value = []
    }
  }

  /**
   * 从数据库加载用户的会话列表
   */
  const loadConversationsFromDB = async () => {
    try {
      const userStore = useUserStore()
      const userId = userStore.userInfo?.id || 1
      
      const response = await getUserSessions(userId)
      const dbSessions = response.data || []
      
      // 转换后端会话格式为前端格式
      conversations.value = dbSessions.map(session => ({
        id: session.id,
        title: session.title || '新对话',
        createdAt: session.createTime || new Date().toISOString(),
        updatedAt: session.updateTime || new Date().toISOString()
      }))
    } catch (error) {
      console.error('加载会话列表失败:', error)
      conversations.value = []
    }
  }

  /**
   * 清空当前会话的消息
   */
  const clearMessages = () => {
    messages.value = []
  }

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const focusInput = () => {
    shouldFocusInput.value = true
  }

  /**
   * 初始化：从数据库加载会话列表
   */
  const initialize = async () => {
    await loadConversationsFromDB()
    
    // 如果有保存的当前会话ID，尝试切换到该会话
    const currentId = localStorage.getItem('chat_current_conversation_id')
    if (currentId && conversations.value.find(conv => conv.id === currentId)) {
      await switchConversation(currentId)
    } else if (conversations.value.length > 0) {
      // 否则选择第一个会话
      await switchConversation(conversations.value[0].id)
    } else {
      // 没有会话，准备新建
      isNewConversation.value = true
    }
  }

  return {
    conversations,
    currentConversationId,
    messages,
    isLoading,
    isStreaming,
    sidebarCollapsed,
    shouldFocusInput,
    isNewConversation,
    currentConversation,
    createConversation,
    setCurrentSessionId,
    addNewConversationToList,
    switchConversation,
    deleteConversation,
    renameConversation,
    updateConversationTitle,
    addMessage,
    updateLastMessage,
    clearMessages,
    toggleSidebar,
    focusInput,
    initialize,
    loadConversationsFromDB
  }
})
