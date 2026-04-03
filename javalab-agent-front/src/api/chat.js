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
 * @param {number} userId - 用户ID（用于权限校验）
 * @returns {Promise} 消息列表
 */
export const getSessionHistory = (sessionId, userId = 1) => {
  // 使用 POST 请求获取会话历史消息，增加用户ID校验
  return apiClient.post('/v1/ai/rag/history', { sessionId, userId })
}

/**
 * 删除会话（逻辑删除）
 * @param {string} sessionId - 会话ID
 * @param {number} userId - 用户ID（用于权限校验）
 * @returns {Promise} 删除结果，true表示成功
 */
export const deleteSession = (sessionId, userId = 1) => {
  // 改为 POST 请求，增加用户ID校验
  return apiClient.post('/v1/ai/rag/sessions/delete', { sessionId, userId })
}

/**
 * 批量删除会话（逻辑删除）
 * @param {Array<string>} sessionIds - 会话ID列表
 * @param {number} userId - 用户ID（用于权限校验）
 * @returns {Promise} 删除结果，true表示成功
 */
export const deleteSessions = (sessionIds, userId = 1) => {
  return apiClient.post('/v1/ai/rag/sessions/delete', { sessionIds, userId })
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
 * @param {string} [params.model] - 大模型名称
 * @param {Object} callbacks - 回调函数集合
 * @param {Function} callbacks.onMessage - 收到消息时的回调 (data: string) => void
 * @param {Function} callbacks.onError - 发生错误时的回调 (error: Error) => void
 * @param {Function} callbacks.onComplete - 完成时的回调 () => void
 * @returns {AbortController} 用于取消请求的控制器
 */
export const sendChatMessage = (params, callbacks) => {
  return sendSseMessage('/api/v1/ai/rag', params, callbacks)
}

export const sendReactAgentMessage = (params, callbacks) => {
  return sendSseMessage('/api/v1/ai/react-agent', params, callbacks)
}

const sendSseMessage = (url, params, callbacks) => {
  const { message, sessionId = '', userId = 1, model } = params
  const { onMessage, onError, onComplete } = callbacks

  const controller = new AbortController()

  const token = localStorage.getItem('token')

  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }

  // 仅在 token 存在时携带 Authorization，避免发送 "Bearer null"
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ message, sessionId, userId, model }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        if (response.status === 40100 || response.status === 40101 || response.status === 401) {
          throw new Error('未登录或无权限，请先登录后再重试')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let eventBuffer = []

      while (true) {
        const { done, value } = await reader.read()

        if (done) {
          if (buffer) {
            processLine(buffer, eventBuffer, onMessage)
          }
          if (eventBuffer.length > 0) {
            onMessage?.(eventBuffer.join(''))
          }
          onComplete?.()
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          processLine(line, eventBuffer, onMessage)
        }
      }
    })
    .catch((error) => {
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
 * @param {string[]} eventBuffer - 事件数据缓存
 * @param {Function} onMessage - 消息回调
 */
function processLine(line, eventBuffer, onMessage) {
  const cleanLine = line.endsWith('\r') ? line.slice(0, -1) : line

  if (!cleanLine) {
    if (eventBuffer.length > 0) {
      const fullMessage = eventBuffer.join('')
      onMessage?.(fullMessage)
      eventBuffer.length = 0
    }
    return
  }

  if (cleanLine.startsWith('data:')) {
    let data = cleanLine.slice(5)

    try {
      const parsed = JSON.parse(data)

      if (parsed.eventType) {
        onMessage?.(parsed)
        return
      }

      if (parsed.sessionId) {
        eventBuffer.push(`[SESSION_ID:${parsed.sessionId}]`)
      } else if (parsed.content !== undefined) {
        eventBuffer.push(parsed.content)
      }
    } catch {
      if (data.length === 0) {
        data = '\n'
      }
      eventBuffer.push(data)
    }
  }
}
