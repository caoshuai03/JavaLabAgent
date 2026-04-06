import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserSessions, getSessionHistory, deleteSession, deleteSessions } from '../api/chat'
import { useUserStore } from './user'

const CURRENT_CONVERSATION_STORAGE_KEY = 'chat_current_conversation_id'
const DRAFT_CONVERSATION_PREFIX = '__draft_conversation__'

export const useChatStore = defineStore('chat', () => {
  // 会话列表
  const conversations = ref([])

  // 当前选中的会话 key：历史会话直接使用 sessionId，新会话使用前端草稿 key
  const activeConversationKey = ref(null)

  // 每个会话单独维护自己的消息、加载和流式状态，避免历史会话切换时串流
  const conversationStates = ref({})

  // 侧边栏折叠状态
  const sidebarCollapsed = ref(false)

  // 是否需要聚焦输入框
  const shouldFocusInput = ref(false)

  // 当前选中的大模型
  const selectedModel = ref('qwen3:8b')

  const chatMode = ref('ask')

  const createConversationState = () => ({
    messages: [],
    isLoading: false,
    isStreaming: false,
    hasLoadedMessages: false,
  })

  const generateDraftConversationKey = () => {
    return `${DRAFT_CONVERSATION_PREFIX}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  const generateMessageId = () => {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  const isDraftConversationKey = (conversationKey) => {
    return typeof conversationKey === 'string' && conversationKey.startsWith(DRAFT_CONVERSATION_PREFIX)
  }

  const ensureConversationState = (conversationKey) => {
    if (!conversationKey) return null

    if (!conversationStates.value[conversationKey]) {
      conversationStates.value[conversationKey] = createConversationState()
    }

    return conversationStates.value[conversationKey]
  }

  const getConversationState = (conversationKey = activeConversationKey.value, createIfMissing = false) => {
    if (!conversationKey) return null

    if (createIfMissing) {
      return ensureConversationState(conversationKey)
    }

    return conversationStates.value[conversationKey] || null
  }

  const selectConversationKey = (conversationKey) => {
    activeConversationKey.value = conversationKey

    if (!conversationKey || isDraftConversationKey(conversationKey)) {
      localStorage.removeItem(CURRENT_CONVERSATION_STORAGE_KEY)
      return
    }

    localStorage.setItem(CURRENT_CONVERSATION_STORAGE_KEY, conversationKey)
  }

  const createDraftConversation = ({ focus = true } = {}) => {
    const draftConversationKey = generateDraftConversationKey()
    const state = ensureConversationState(draftConversationKey)

    // 新建草稿会话时重置该草稿自己的显示状态，不影响其他会话中的流式任务
    state.messages = []
    state.isLoading = false
    state.isStreaming = false
    state.hasLoadedMessages = false

    selectConversationKey(draftConversationKey)

    if (focus) {
      focusInput()
    }

    return draftConversationKey
  }

  const currentConversationId = computed(() => {
    return isDraftConversationKey(activeConversationKey.value) ? null : activeConversationKey.value
  })

  const messages = computed(() => {
    return getConversationState(activeConversationKey.value)?.messages || []
  })

  const isLoading = computed(() => {
    return getConversationState(activeConversationKey.value)?.isLoading || false
  })

  const isStreaming = computed(() => {
    return getConversationState(activeConversationKey.value)?.isStreaming || false
  })

  const isNewConversation = computed(() => {
    return isDraftConversationKey(activeConversationKey.value)
  })

  const currentConversation = computed(() => {
    return conversations.value.find((conv) => conv.id === currentConversationId.value)
  })

  /**
   * 创建新对话
   * 注意：新对话的 ID 由后端在第一次发送消息时生成
   */
  const createConversation = () => {
    return createDraftConversation()
  }

  /**
   * 设置当前会话ID（由后端返回的 sessionId）
   * @param {string} sessionId - 后端返回的会话ID
   * @param {string} [sourceConversationKey] - 流式开始时所属的会话 key
   */
  const setCurrentSessionId = (sessionId, sourceConversationKey = activeConversationKey.value) => {
    if (!sessionId) return

    const sourceState = getConversationState(sourceConversationKey, false)

    if (sourceConversationKey && sourceConversationKey !== sessionId && sourceState) {
      conversationStates.value[sessionId] = sourceState
      delete conversationStates.value[sourceConversationKey]
    } else {
      ensureConversationState(sessionId)
    }

    // 只有当前正在看的就是这条流时，才切换当前选中项，避免后台流式输出打断当前页面
    if (activeConversationKey.value === sourceConversationKey || !activeConversationKey.value) {
      selectConversationKey(sessionId)
    }
  }

  const setConversationLoading = (conversationKey, loading) => {
    const state = ensureConversationState(conversationKey)
    if (state) {
      state.isLoading = loading
    }
  }

  const setConversationStreaming = (conversationKey, streaming) => {
    const state = ensureConversationState(conversationKey)
    if (state) {
      state.isStreaming = streaming
    }
  }

  const getConversationMessages = (conversationKey = activeConversationKey.value) => {
    return getConversationState(conversationKey)?.messages || []
  }

  const getLastMessage = (conversationKey = activeConversationKey.value) => {
    const conversationMessages = getConversationMessages(conversationKey)
    return conversationMessages.length > 0 ? conversationMessages[conversationMessages.length - 1] : null
  }

  /**
   * 切换到指定会话，优先展示内存缓存；仅在首次进入且本地没有缓存时才从数据库加载
   * @param {string} conversationId - 会话ID
   */
  const switchConversation = async (conversationId) => {
    selectConversationKey(conversationId)

    const conversation = conversations.value.find((conv) => conv.id === conversationId)
    const state = getConversationState(conversationId, false)

    if (conversation && !(state?.isStreaming || state?.hasLoadedMessages || state?.messages.length > 0)) {
      await loadConversationMessagesFromDB(conversationId)
    } else {
      ensureConversationState(conversationId)
    }

    // 触发输入框聚焦
    focusInput()
  }

  const removeConversationState = (conversationKey) => {
    if (conversationKey && conversationStates.value[conversationKey]) {
      delete conversationStates.value[conversationKey]
    }
  }

  /**
   * 删除对话（调用后端API进行逻辑删除）
   * @param {string} conversationId - 会话ID
   * @returns {Promise<boolean>} 删除是否成功
   */
  const deleteConversation = async (conversationId) => {
    try {
      const userStore = useUserStore()
      const userId = userStore.userInfo?.id || 1

      // 调用后端API删除会话，传递 userId 进行权限校验
      const response = await deleteSession(conversationId, userId)

      // 删除成功后更新本地状态
      if (response.data === true) {
        const index = conversations.value.findIndex((conv) => conv.id === conversationId)
        if (index !== -1) {
          conversations.value.splice(index, 1)
        }

        removeConversationState(conversationId)

        // 如果删除的是当前会话，切换到其他会话
        if (conversationId === currentConversationId.value) {
          if (conversations.value.length > 0) {
            await switchConversation(conversations.value[0].id)
          } else {
            createDraftConversation({ focus: false })
          }
        }

        console.log('会话删除成功:', conversationId)
        return true
      } else {
        console.error('删除会话失败: 后端返回false')
        return false
      }
    } catch (error) {
      console.error('删除会话失败:', error)
      return false
    }
  }

  /**
   * 批量删除对话
   * @param {Array<string>} conversationIds - 会话ID列表
   * @returns {Promise<boolean>} 删除是否成功
   */
  const deleteConversations = async (conversationIds) => {
    try {
      if (!conversationIds || conversationIds.length === 0) return false

      const userStore = useUserStore()
      const userId = userStore.userInfo?.id || 1

      // 调用后端API批量删除会话
      const response = await deleteSessions(conversationIds, userId)

      // 删除成功后更新本地状态
      if (response.data === true) {
        // 过滤掉已删除的会话
        conversations.value = conversations.value.filter(
          (conv) => !conversationIds.includes(conv.id),
        )

        conversationIds.forEach((conversationId) => {
          removeConversationState(conversationId)
        })

        // 如果当前会话被删除了，切换到其他会话
        if (conversationIds.includes(currentConversationId.value)) {
          if (conversations.value.length > 0) {
            await switchConversation(conversations.value[0].id)
          } else {
            createDraftConversation({ focus: false })
          }
        }

        console.log('批量删除会话成功:', conversationIds)
        return true
      } else {
        console.error('批量删除会话失败: 后端返回false')
        return false
      }
    } catch (error) {
      console.error('批量删除会话失败:', error)
      return false
    }
  }

  /**
   * 重命名会话
   * 注意：仅更新前端显示标题，不修改 updatedAt（时间由数据库管理）
   * TODO: 后续可以添加后端更新接口
   */
  const renameConversation = (conversationId, newTitle) => {
    const conversation = conversations.value.find((conv) => conv.id === conversationId)
    if (conversation) {
      conversation.title = newTitle || '新对话'
      // 不更新 updatedAt，保持数据库时间
    }
  }

  /**
   * 更新会话标题（根据第一条消息）
   * 注意：仅更新前端显示标题，不修改 updatedAt（时间由数据库管理）
   */
  const updateConversationTitle = (conversationId, firstMessage) => {
    const conversation = conversations.value.find((conv) => conv.id === conversationId)
    if (conversation && conversation.title === '新对话') {
      const title = firstMessage.length > 30 ? firstMessage.substring(0, 30) + '...' : firstMessage
      conversation.title = title
      // 不更新 updatedAt，保持数据库时间
    }
  }

  /**
   * 添加消息到指定会话
   * @param {string} sender - 发送者类型（'user' 或 'assistant'）
   * @param {string} content - 消息内容
   * @param {string} [conversationKey] - 会话 key
   * @returns {object|null} 消息对象
   */
  const addMessage = (sender, content, conversationKey = activeConversationKey.value) => {
    const state = ensureConversationState(conversationKey)
    if (!state) return null

    const message = {
      id: generateMessageId(),
      sender: sender,
      content: content,
      timestamp: new Date().toISOString(),
      toolEvents: [],
      isComplete: sender !== 'assistant',
      feedbackState: null,
    }

    state.messages.push(message)

    return message
  }

  /**
   * 将当前新对话添加到会话列表
   * 注意：时间使用null占位，会在下次刷新会话列表时从数据库同步
   * @param {string} sessionId - 后端返回的会话ID
   * @param {string} title - 会话标题（通常是第一条消息）
   */
  const addNewConversationToList = (sessionId, title) => {
    const existingConversation = conversations.value.find((conversation) => conversation.id === sessionId)

    if (existingConversation) {
      existingConversation.title = title.length > 30 ? title.substring(0, 30) + '...' : title
      return
    }

    const newConversation = {
      id: sessionId,
      title: title.length > 30 ? title.substring(0, 30) + '...' : title,
      createdAt: null, // 时间由数据库管理，下次刷新会话列表时同步
      updatedAt: null, // 时间由数据库管理，下次刷新会话列表时同步
    }

    // 添加到列表顶部
    conversations.value.unshift(newConversation)
  }

  /**
   * 更新指定会话最后一条消息内容（用于流式响应）
   * @param {string} content - 最新消息内容
   * @param {string} [conversationKey] - 会话 key
   */
  const updateLastMessage = (content, conversationKey = activeConversationKey.value) => {
    const lastMessage = getLastMessage(conversationKey)
    if (lastMessage && lastMessage.sender === 'assistant') {
      lastMessage.content = content
    }
  }

  const markLastAssistantMessageComplete = (conversationKey = activeConversationKey.value) => {
    const lastMessage = getLastMessage(conversationKey)
    if (lastMessage && lastMessage.sender === 'assistant') {
      lastMessage.isComplete = true
    }
  }

  const setMessageFeedbackState = (
    messageId,
    feedbackState,
    conversationKey = activeConversationKey.value,
  ) => {
    const state = getConversationState(conversationKey)
    if (!state) return

    const targetMessage = state.messages.find((message) => message.id === messageId)
    if (targetMessage) {
      targetMessage.feedbackState = feedbackState
    }
  }

  const addToolEventToLastMessage = (toolEvent, conversationKey = activeConversationKey.value) => {
    const lastMessage = getLastMessage(conversationKey)
    if (lastMessage && lastMessage.sender === 'assistant') {
      if (!Array.isArray(lastMessage.toolEvents)) {
        lastMessage.toolEvents = []
      }
      lastMessage.toolEvents.push(toolEvent)
    }
  }

  /**
   * 从数据库加载会话的历史消息
   * @param {string} sessionId - 会话ID
   * @param {boolean} [force=false] - 是否强制刷新
   */
  const loadConversationMessagesFromDB = async (sessionId, force = false) => {
    const state = ensureConversationState(sessionId)

    if (!force && (state.isStreaming || state.hasLoadedMessages || state.messages.length > 0)) {
      return state.messages
    }

    try {
      const userStore = useUserStore()
      const userId = userStore.userInfo?.id || 1

      state.isLoading = true

      const response = await getSessionHistory(sessionId, userId)
      const dbMessages = response.data || []

      // 转换后端消息格式为前端格式
      state.messages = dbMessages.map((msg) => {
        let content = msg.content || ''
        let toolEvents = []

        // 解析思考过程
        // 格式: <!-- thinking_process_start -->[...]<!-- thinking_process_end -->
        const thinkingRegex =
          /<!-- thinking_process_start -->([\s\S]*?)<!-- thinking_process_end -->\n?/
        const match = content.match(thinkingRegex)

        if (match) {
          try {
            // 解析 JSON 数组
            const eventsJson = match[1]
            // 后端保存的是 JSON 对象数组的字符串形式
            const events = JSON.parse(eventsJson)

            // 转换事件格式 (如果需要) - 目前看后端返回的结构和前端需要的结构基本一致
            // 前端 MessageItem 需要 toolEvents 包含 eventType, payload 等字段
            // 后端 eventJson 生成的正是这种结构
            toolEvents = events

            // 从内容中移除思考过程部分
            content = content.replace(match[0], '')
          } catch (e) {
            console.error('解析思考过程失败:', e)
          }
        }

        return {
          id: msg.id || generateMessageId(),
          sender: msg.role === 'user' ? 'user' : 'assistant',
          content: content,
          timestamp: msg.createdAt || new Date().toISOString(),
          toolEvents: toolEvents,
          isComplete: true,
          feedbackState: null,
        }
      })
      state.hasLoadedMessages = true

      return state.messages
    } catch (error) {
      console.error('加载会话历史失败:', error)
      state.messages = []
      state.hasLoadedMessages = false
      return []
    } finally {
      state.isLoading = false
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
      // 注意：后端字段名是 createdAt/updatedAt（驼峰命名）
      conversations.value = dbSessions.map((session) => ({
        id: session.id,
        title: session.title || '新对话',
        createdAt: session.createdAt || null,
        updatedAt: session.updatedAt || null,
      }))
    } catch (error) {
      console.error('加载会话列表失败:', error)
      conversations.value = []
    }
  }

  /**
   * 清空当前会话的消息
   */
  const clearMessages = (conversationKey = activeConversationKey.value) => {
    const state = ensureConversationState(conversationKey)
    if (state) {
      state.messages = []
      state.hasLoadedMessages = false
    }
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

    // 如果当前已经是新对话状态（比如从其他页面点击“新对话”跳转过来时），则不自动加载历史会话
    if (isDraftConversationKey(activeConversationKey.value)) {
      return
    }

    // 如果有保存的当前会话ID，尝试切换到该会话
    const currentId = localStorage.getItem(CURRENT_CONVERSATION_STORAGE_KEY)
    if (currentId && conversations.value.find((conv) => conv.id === currentId)) {
      await switchConversation(currentId)
    } else if (conversations.value.length > 0) {
      // 否则选择第一个会话
      await switchConversation(conversations.value[0].id)
    } else {
      // 没有会话，准备新建一个前端草稿会话
      createDraftConversation({ focus: false })
    }
  }

  return {
    conversations,
    currentConversationId,
    activeConversationKey,
    messages,
    isLoading,
    isStreaming,
    sidebarCollapsed,
    shouldFocusInput,
    isNewConversation,
    selectedModel,
    chatMode,
    currentConversation,
    createConversation,
    setCurrentSessionId,
    setConversationLoading,
    setConversationStreaming,
    getConversationMessages,
    getLastMessage,
    addNewConversationToList,
    switchConversation,
    deleteConversation,
    deleteConversations,
    renameConversation,
    updateConversationTitle,
    addMessage,
    updateLastMessage,
    markLastAssistantMessageComplete,
    setMessageFeedbackState,
    addToolEventToLastMessage,
    clearMessages,
    toggleSidebar,
    focusInput,
    initialize,
    loadConversationsFromDB,
    loadConversationMessagesFromDB,
    isDraftConversationKey,
  }
})
