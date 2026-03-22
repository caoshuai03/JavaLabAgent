import apiClient from './index'

/**
 * MCP工具管理API
 * 对应后端 McpController 的接口
 */

/**
 * 获取所有MCP服务器配置列表
 * @returns {Promise} { servers: [...], totalTools: number }
 */
export const getMcpServers = () => {
  return apiClient.get('/v1/mcp/servers')
}

/**
 * 添加MCP服务器
 * @param {Object} serverData - 服务器配置
 * @param {string} serverData.name - 服务器名称
 * @param {string} serverData.type - 传输类型: stdio / http
 * @param {string} [serverData.command] - stdio模式的命令
 * @param {Array<string>} [serverData.args] - stdio模式的参数列表
 * @param {string} [serverData.url] - http模式的URL
 * @param {Object} [serverData.env] - 环境变量
 * @param {string} [serverData.description] - 描述
 * @param {boolean} [serverData.enabled] - 是否启用
 * @returns {Promise} { success: boolean, message: string }
 */
export const addMcpServer = (serverData) => {
  return apiClient.post('/v1/mcp/servers', serverData)
}

/**
 * 删除MCP服务器
 * @param {string} name - 服务器名称
 * @returns {Promise} { success: boolean, message: string }
 */
export const removeMcpServer = (name) => {
  return apiClient.delete(`/v1/mcp/servers/${encodeURIComponent(name)}`)
}

/**
 * 更新MCP服务器配置
 * @param {string} name - 服务器名称
 * @param {Object} updateData - 要更新的字段
 * @returns {Promise} { success: boolean, message: string }
 */
export const updateMcpServer = (name, updateData) => {
  return apiClient.put(`/v1/mcp/servers/${encodeURIComponent(name)}`, updateData)
}

/**
 * 测试MCP服务器连接
 * @param {string} name - 服务器名称
 * @returns {Promise} { success: boolean, message: string }
 */
export const testMcpServer = (name) => {
  return apiClient.post(`/v1/mcp/servers/${encodeURIComponent(name)}/test`)
}

/**
 * 重启MCP服务器
 * @param {string} name - 服务器名称
 * @returns {Promise} { success: boolean, message: string }
 */
export const restartMcpServer = (name) => {
  return apiClient.post(`/v1/mcp/servers/${encodeURIComponent(name)}/restart`)
}

/**
 * 获取所有可用的MCP工具列表
 * @returns {Promise} { tools: [...], count: number }
 */
export const getMcpTools = () => {
  return apiClient.get('/v1/mcp/tools')
}

/**
 * 刷新指定服务器的工具列表
 * @param {string} name - 服务器名称
 * @returns {Promise} { success: boolean, message: string, tools: [...] }
 */
export const refreshMcpServerTools = (name) => {
  return apiClient.post(`/v1/mcp/servers/${encodeURIComponent(name)}/refresh`)
}
