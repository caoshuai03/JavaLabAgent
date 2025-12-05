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
  return apiClient.get('/v1/ai/rag/sessions', {
    params: { userId }
  })
}

/**
 * 获取会话的历史消息
 * @param {string} sessionId - 会话ID
 * @returns {Promise} 消息列表
 */
export const getSessionHistory = (sessionId) => {
  return apiClient.get('/v1/ai/rag/history', {
    params: { sessionId }
  })
}
