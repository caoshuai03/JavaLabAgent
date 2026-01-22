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

  // 从本地存储中读取 token（与 axios 拦截器保持一致）
  // 注意：后端 JwtTokenUserInterceptor 期望请求头中携带 "Bearer <token>"
  const token = localStorage.getItem('token')

  // 构建请求头
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream',
  }

  // 仅在 token 存在时携带 Authorization，避免发送 "Bearer null"
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  // 发起 POST 请求
  fetch('/api/v1/ai/rag', {
    method: 'POST',
    headers,
    body: JSON.stringify({ message, sessionId, userId }),
    signal: controller.signal,
  })
    .then(async (response) => {
      // 检查响应状态
      if (!response.ok) {
        // 40100/40101 是后端自定义错误码（直接作为 HTTP status 返回）
        // 这里给出更明确的错误，便于前端提示用户先登录
        if (response.status === 40100 || response.status === 40101 || response.status === 401) {
          throw new Error('未登录或无权限，请先登录后再重试')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      // 使用 ReadableStream 读取 SSE 响应
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = '' // 缓冲区，用于处理不完整的数据块
      let eventBuffer = [] // 缓冲区，用于处理 SSE 事件的多行数据

      while (true) {
        const { done, value } = await reader.read()

        if (done) {
          // 处理缓冲区中剩余的数据
          if (buffer) {
            processLine(buffer, eventBuffer, onMessage)
          }
          // 发送剩余的事件数据
          if (eventBuffer.length > 0) {
            onMessage?.(eventBuffer.join(''))
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
          processLine(line, eventBuffer, onMessage)
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
 * @param {string[]} eventBuffer - 事件数据缓存
 * @param {Function} onMessage - 消息回调
 */
function processLine(line, eventBuffer, onMessage) {
  // 处理 CR 回车符（兼容 Windows 换行 \r\n）
  const cleanLine = line.endsWith('\r') ? line.slice(0, -1) : line

  // 空行表示当前事件结束，分发累积的数据
  if (!cleanLine) {
    if (eventBuffer.length > 0) {
      // 将多行 data 合并，改为直接连接
      // 因为后端返回的流式数据中，如果出现连在一起的 data: 行，通常是因为它们属于同一个输出片段
      // 而之前的换行符处理逻辑会导致额外的换行（如代码块中）或断行（如 ReentrantLock）
      const fullMessage = eventBuffer.join('')
      onMessage?.(fullMessage)
      eventBuffer.length = 0 // 清空缓存
    }
    return
  }

  // 解析 SSE 格式数据（data: xxx）
  if (cleanLine.startsWith('data:')) {
    // 去除 "data:" 前缀
    let data = cleanLine.slice(5)

    // 如果 data 为空字符串，说明是换行符
    if (data.length === 0) {
      data = '\n'
    }

    // 收集数据
    eventBuffer.push(data)
  }
}
