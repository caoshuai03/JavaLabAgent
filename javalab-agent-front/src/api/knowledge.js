import apiClient from './index'

/**
 * 知识库管理API
 */
export const knowledgeApi = {
  /**
   * 上传文件
   * @param {FormData} formData - 包含文件的FormData对象
   * @returns {Promise} 上传结果
   */
  uploadFiles: (formData) => {
    return apiClient.post('/v1/knowledge/file/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  /**
   * 分页查询文件列表
   * @param {Object} params - 查询参数
   * @param {number} params.page - 页码
   * @param {number} params.pageSize - 每页数量
   * @param {string} [params.fileName] - 文件名（可选，用于搜索）
   * @returns {Promise} 文件列表数据
   */
  getFileList: (params) => {
    return apiClient.get('/v1/knowledge/contents', { params })
  },

  /**
   * 删除文件
   * @param {Array<number>} ids - 文件ID数组
   * @returns {Promise} 删除结果
   */
  deleteFiles: (ids) => {
    // Spring Boot接受List参数时，需要将数组转换为查询字符串格式
    const params = new URLSearchParams()
    ids.forEach(id => params.append('ids', id))
    return apiClient.delete(`/v1/knowledge/delete?${params.toString()}`)
  },

  /**
   * 下载文件
   * @param {Array<number>} ids - 文件ID数组
   * @returns {Promise} 文件blob数据
   */
  downloadFiles: (ids) => {
    // Spring Boot接受List参数时，需要将数组转换为查询字符串格式
    const params = new URLSearchParams()
    ids.forEach(id => params.append('ids', id))
    return apiClient.get(`/v1/knowledge/download?${params.toString()}`, {
      responseType: 'blob'
    })
  },

  /**
   * 下载单个文件（使用文件流）
   * @param {number} id - 文件ID
   * @returns {Promise} 文件blob数据
   */
  downloadFile: (id) => {
    return apiClient.get(`/v1/knowledge/downloadFile/${id}`, {
      responseType: 'blob'
    })
  }
}

