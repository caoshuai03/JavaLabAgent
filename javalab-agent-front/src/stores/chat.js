import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref([])

  const currentConversationId = ref(null)

  const messages = ref([])

  const isLoading = ref(false)

  const isStreaming = ref(false)

  const sidebarCollapsed = ref(false)

  const shouldFocusInput = ref(false)

  const currentConversation = computed(() => {
    return conversations.value.find(conv => conv.id === currentConversationId.value)
  })

  const generateConversationId = () => {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  const generateMessageId = () => {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  const createConversation = () => {
    const newId = generateConversationId()
    currentConversationId.value = newId
    messages.value = []

    localStorage.setItem('chat_current_conversation_id', newId)

    focusInput()

    return newId
  }

  const addConversationToList = () => {
    if (!currentConversationId.value) return

    const exists = conversations.value.find(conv => conv.id === currentConversationId.value)
    if (exists) return

    const newConversation = {
      id: currentConversationId.value,
      title: '新对话',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }

    conversations.value.unshift(newConversation)

    // 保存到 localStorage
    saveConversationsToStorage()
  }

  const switchConversation = (conversationId) => {
    if (currentConversationId.value) {
      saveCurrentConversationMessages()
    }

    currentConversationId.value = conversationId
    const conversation = conversations.value.find(conv => conv.id === conversationId)

    if (conversation) {
      loadConversationMessages(conversationId)
      saveConversationsToStorage()
    } else {
      messages.value = []
    }

    // 触发输入框聚焦
    focusInput()
  }

  // 删除对话
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
        }
      }

      // 从 localStorage 删除消息
      localStorage.removeItem(`chat_messages_${conversationId}`)
      saveConversationsToStorage()
    }
  }

  const renameConversation = (conversationId, newTitle) => {
    const conversation = conversations.value.find(conv => conv.id === conversationId)
    if (conversation) {
      conversation.title = newTitle || '新对话'
      conversation.updatedAt = new Date().toISOString()
      saveConversationsToStorage()
    }
  }

  const updateConversationTitle = (conversationId, firstMessage) => {
    const conversation = conversations.value.find(conv => conv.id === conversationId)
    if (conversation && conversation.title === '新对话') {
      const title = firstMessage.length > 30
        ? firstMessage.substring(0, 30) + '...'
        : firstMessage
      conversation.title = title
      conversation.updatedAt = new Date().toISOString()
      saveConversationsToStorage()
    }
  }

  const addMessage = (sender, content, conversationId = null) => {
    const convId = conversationId || currentConversationId.value

    if (!convId) {
      const newId = createConversation()
      conversationId = newId
    }

    if (sender === 'user') {
      const isNewConversation = messages.value.length === 0
      const isInList = conversations.value.find(conv => conv.id === convId) !== undefined

      if (isNewConversation && !isInList) {
        addConversationToList()
      }
    }

    const message = {
      id: generateMessageId(),
      sender: sender,
      content: content,
      timestamp: new Date().toISOString()
    }

    messages.value.push(message)

    if (sender === 'user' && messages.value.filter(m => m.sender === 'user').length === 1) {
      updateConversationTitle(convId, content)
    } else {
      const conversation = conversations.value.find(conv => conv.id === convId)
      if (conversation) {
        conversation.updatedAt = new Date().toISOString()
        saveConversationsToStorage()
      }
    }

    // 保存消息到 localStorage
    saveCurrentConversationMessages()

    return message
  }

  const updateLastMessage = (content) => {
    if (messages.value.length > 0) {
      const lastMessage = messages.value[messages.value.length - 1]
      if (lastMessage.sender === 'assistant') {
        lastMessage.content = content
        debounceSaveMessages()
      }
    }
  }

  const saveCurrentConversationMessages = () => {
    if (currentConversationId.value) {
      localStorage.setItem(
        `chat_messages_${currentConversationId.value}`,
        JSON.stringify(messages.value)
      )
    }
  }

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

  const saveConversationsToStorage = () => {
    localStorage.setItem('chat_conversations', JSON.stringify(conversations.value))
    if (currentConversationId.value) {
      localStorage.setItem('chat_current_conversation_id', currentConversationId.value)
    }
  }

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

  const clearMessages = () => {
    messages.value = []
    if (currentConversationId.value) {
      saveCurrentConversationMessages()
    }
  }

  let saveTimer = null
  const debounceSaveMessages = () => {
    if (saveTimer) {
      clearTimeout(saveTimer)
    }
    saveTimer = setTimeout(() => {
      saveCurrentConversationMessages()
    }, 1000)
  }

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const focusInput = () => {
    shouldFocusInput.value = true
  }

  const initialize = () => {
    loadConversationsFromStorage()
  }

  return {
    conversations,
    currentConversationId,
    messages,
    isLoading,
    isStreaming,
    sidebarCollapsed,
    shouldFocusInput,
    currentConversation,
    createConversation,
    addConversationToList,
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
    saveCurrentConversationMessages
  }
})
