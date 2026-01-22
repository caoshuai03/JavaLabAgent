import apiClient from './index'

/**
 * 用户反馈相关API
 */
export const feedbackApi = {
  /**
   * 提交用户反馈
   * @param {Object} data 反馈数据
   * @param {number} data.type 反馈类型：0-其它 1-BUG 2-建议 3-投诉
   * @param {string} data.title 反馈标题（可选）
   * @param {string} data.content 反馈内容（必填）
   * @param {number} data.priority 优先级：0-低 1-中 2-高（可选）
   * @param {number} data.messageId 关联消息ID（可选）
   * @param {string} data.sessionId 关联会话ID（可选）
   * @returns {Promise} 提交结果
   */
  submit: (data) => apiClient.post('/v1/feedback/submit', data)
}

export default feedbackApi
