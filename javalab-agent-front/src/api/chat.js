import apiClient from './index'

/**
 * 聊天相关 API
 * 使用数据库持久化存储会话和消息
 */

/**
 * 获取用户的会话列表
 * @param {number} userId - 用户ID
 * @returns {Promise} 会话列表
 */
export const getUserSessions = (userId) => {
  // 使用 POST 请求获取用户会话列表
  return apiClient.post('/v1/ai/rag/sessions', { userId })
}

/**
 * 获取会话的历史消息
 * @param {string} sessionId - 会话ID
 * @returns {Promise} 消息列表
 */
export const getSessionHistory = (sessionId) => {
  // 使用 POST 请求获取会话历史消息
  return apiClient.post('/v1/ai/rag/history', { sessionId })
}

/**
 * 删除会话（逻辑删除）
 * @param {string} sessionId - 会话ID
 * @returns {Promise} 删除结果，true表示成功
 */
export const deleteSession = (sessionId) => {
  return apiClient.delete(`/v1/ai/rag/sessions/${sessionId}`)
}

/**
 * 发送RAG对话消息（POST方式，支持SSE流式响应）
 * 
 * 使用 fetch + ReadableStream 处理 SSE 流式响应，
 * 相比 EventSource（仅支持GET），POST方式更安全且支持更长的消息内容。
 * 
 * @param {Object} params - 请求参数
 * @param {string} params.message - 用户消息
 * @param {string} [params.sessionId] - 会话ID，新会话时为空
 * @param {number} [params.userId=1] - 用户ID
 * @param {Object} callbacks - 回调函数集合
 * @param {Function} callbacks.onMessage - 收到消息时的回调 (data: string) => void
 * @param {Function} callbacks.onError - 发生错误时的回调 (error: Error) => void
 * @param {Function} callbacks.onComplete - 完成时的回调 () => void
 * @returns {AbortController} 用于取消请求的控制器
 */
export const sendChatMessage = (params, callbacks) => {
  const { message, sessionId = '', userId = 1 } = params
  const { onMessage, onError, onComplete } = callbacks
  
  // 创建 AbortController 用于取消请求
  const controller = new AbortController()
  
  // 发起 POST 请求
  fetch('/api/v1/ai/rag', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    body: JSON.stringify({ message, sessionId, userId }),
    signal: controller.signal,
  })
    .then(async (response) => {
      // 检查响应状态
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      // 使用 ReadableStream 读取 SSE 响应
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = '' // 缓冲区，用于处理不完整的数据块
      
      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          // 处理缓冲区中剩余的数据
          if (buffer.trim()) {
            processSSEData(buffer, onMessage)
          }
          onComplete?.()
          break
        }
        
        // 解码数据并添加到缓冲区
        buffer += decoder.decode(value, { stream: true })
        
        // 按换行符分割，处理完整的消息
        const lines = buffer.split('\n')
        // 保留最后一个可能不完整的行
        buffer = lines.pop() || ''
        
        // 处理每一行
        for (const line of lines) {
          processSSEData(line, onMessage)
        }
      }
    })
    .catch((error) => {
      // 忽略用户主动取消的错误
      if (error.name !== 'AbortError') {
        console.error('SSE请求错误:', error)
        onError?.(error)
      }
    })
  
  return controller
}

/**
 * 处理 SSE 数据行
 * @param {string} line - 数据行
 * @param {Function} onMessage - 消息回调
 */
function processSSEData(line, onMessage) {
  const trimmedLine = line.trim()
  
  // 跳过空行和注释
  if (!trimmedLine || trimmedLine.startsWith(':')) {
    return
  }
  
  // 解析 SSE 格式数据（data: xxx）
  if (trimmedLine.startsWith('data:')) {
    const data = trimmedLine.slice(5).trim()
    if (data) {
      onMessage?.(data)
    }
  }
}
