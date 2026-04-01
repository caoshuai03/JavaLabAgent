import apiClient from './index'

/**
 * 获取所有 Skills 列表
 */
export const getSkills = () => {
  return apiClient.get('/v1/skills')
}

/**
 * 获取指定 Skill 的详细信息
 * @param {string} name - Skill 名称
 */
export const getSkillDetail = (name) => {
  return apiClient.get(`/v1/skills/${name}`)
}

/**
 * 刷新 Skills 列表（重新扫描）
 */
export const refreshSkills = () => {
  return apiClient.post('/v1/skills/refresh')
}
