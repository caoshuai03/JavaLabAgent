<template>
  <div class="knowledge-container">
    <Sidebar />
    <div class="knowledge-main">
      <div class="knowledge-content">
        <!-- 顶部操作栏 -->
        <div class="toolbar">
          <div class="toolbar-left">
            <button @click="handleUpload" class="upload-button" :disabled="uploading">
              <UploadIcon :size="18" />
              <span>上传文件</span>
            </button>
            <input
              ref="fileInput"
              type="file"
              multiple
              accept=".pdf,.doc,.docx,.txt,.md"
              @change="handleFileSelect"
              style="display: none"
            />
          </div>
          <div class="toolbar-center">
            <div class="search-box">
              <SearchIcon :size="18" />
              <input
                v-model="searchKeyword"
                type="text"
                placeholder="搜索文件名..."
                @input="handleSearch"
              />
              <button
                v-if="searchKeyword"
                @click="clearSearch"
                class="clear-search"
              >
                ×
              </button>
            </div>
          </div>
          <div class="toolbar-right">
            <button
              v-if="selectedIds.length > 0"
              @click="handleBatchDelete"
              class="batch-button delete"
              :disabled="deleting"
            >
              <TrashIcon :size="16" />
              <span>批量删除 ({{ selectedIds.length }})</span>
            </button>
            <button
              v-if="selectedIds.length > 0"
              @click="handleBatchDownload"
              class="batch-button download"
              :disabled="downloading"
            >
              <DownloadIcon :size="16" />
              <span>批量下载 ({{ selectedIds.length }})</span>
            </button>
          </div>
        </div>

        <!-- 文件列表表格 -->
        <div class="table-container">
          <table class="file-table">
            <thead>
              <tr>
                <th class="checkbox-col">
                  <input
                    type="checkbox"
                    :checked="isAllSelected"
                    @change="handleSelectAll"
                  />
                </th>
                <th class="name-col">文件名</th>
                <th class="time-col">上传时间</th>
                <th class="action-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading" class="loading-row">
                <td colspan="4" class="loading-cell">
                  <div class="loading-spinner">加载中...</div>
                </td>
              </tr>
              <tr v-else-if="fileList.length === 0" class="empty-row">
                <td colspan="4" class="empty-cell">
                  <div class="empty-state">
                    <FolderIcon :size="48" />
                    <p>{{ searchKeyword ? '未找到相关文件' : '暂无文件，请上传文件' }}</p>
                  </div>
                </td>
              </tr>
              <tr
                v-else
                v-for="file in fileList"
                :key="file.id"
                :class="{ selected: selectedIds.includes(file.id) }"
                @click="handleRowClick(file.id)"
              >
                <td class="checkbox-col" @click.stop>
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(file.id)"
                    @change="handleSelectFile(file.id, $event.target.checked)"
                  />
                </td>
                <td class="name-col">
                  <div class="file-name" :title="file.fileName">
                    {{ file.fileName }}
                  </div>
                </td>
                <td class="time-col">
                  {{ formatDate(file.createTime) }}
                </td>
                <td class="action-col" @click.stop>
                  <button
                    @click="handleDownload(file.id)"
                    class="action-button download"
                    :disabled="downloading"
                    title="下载"
                  >
                    <DownloadIcon :size="16" />
                  </button>
                  <button
                    @click="handleDelete(file.id, file.fileName)"
                    class="action-button delete"
                    :disabled="deleting"
                    title="删除"
                  >
                    <TrashIcon :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页组件 -->
        <div v-if="!loading && fileList.length > 0" class="pagination">
          <div class="pagination-info">
            共 {{ total }} 条，第 {{ currentPage }} / {{ totalPages }} 页
          </div>
          <div class="pagination-controls">
            <button
              @click="goToPage(currentPage - 1)"
              :disabled="currentPage === 1"
              class="page-button"
            >
              上一页
            </button>
            <input
              v-model.number="pageInput"
              type="number"
              min="1"
              :max="totalPages"
              @keyup.enter="goToPage(pageInput)"
              class="page-input"
            />
            <span>/ {{ totalPages }}</span>
            <button
              @click="goToPage(currentPage + 1)"
              :disabled="currentPage === totalPages"
              class="page-button"
            >
              下一页
            </button>
            <select v-model="pageSize" @change="handlePageSizeChange" class="page-size-select">
              <option :value="10">10条/页</option>
              <option :value="20">20条/页</option>
              <option :value="50">50条/页</option>
            </select>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { knowledgeApi } from '../api/knowledge'
import { useChatStore } from '../stores/chat'
import Sidebar from '../components/Sidebar.vue'
import UploadIcon from '../components/icons/UploadIcon.vue'
import DownloadIcon from '../components/icons/DownloadIcon.vue'
import TrashIcon from '../components/icons/TrashIcon.vue'
import FolderIcon from '../components/icons/FolderIcon.vue'
import SearchIcon from '../components/icons/SearchIcon.vue'

// 初始化 chatStore
const chatStore = useChatStore()

// 状态
const fileList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')
const selectedIds = ref([])
const loading = ref(false)
const uploading = ref(false)
const deleting = ref(false)
const downloading = ref(false)
const fileInput = ref(null)
const pageInput = ref(1)

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
const isAllSelected = computed(() => {
  return fileList.value.length > 0 && selectedIds.value.length === fileList.value.length
})

// 防抖搜索
let searchTimer = null
const handleSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    fetchFileList()
  }, 300)
}

const clearSearch = () => {
  searchKeyword.value = ''
  currentPage.value = 1
  fetchFileList()
}

const fetchFileList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    if (searchKeyword.value) {
      params.fileName = searchKeyword.value
    }
    const response = await knowledgeApi.getFileList(params)
    if (response.data.code === 0) {
      const data = response.data.data
      fileList.value = data.records || data.list || []
      total.value = data.total || 0
      pageInput.value = currentPage.value
    } else {
      console.error('获取文件列表失败:', response.data.message)
      alert('获取文件列表失败: ' + (response.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('获取文件列表错误:', error)
    alert('获取文件列表失败: ' + (error.message || '网络错误'))
  } finally {
    loading.value = false
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const handleUpload = () => {
  fileInput.value?.click()
}

const handleFileSelect = async (event) => {
  const files = Array.from(event.target.files)
  if (files.length === 0) return

  uploading.value = true
  try {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('file', file)
    })

    const response = await knowledgeApi.uploadFiles(formData)
    if (response.data.code === 0) {
      alert('文件上传成功')
      fetchFileList()
      // 清空文件选择
      if (fileInput.value) {
        fileInput.value.value = ''
      }
    } else {
      alert('文件上传失败: ' + (response.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('上传文件错误:', error)
    alert('文件上传失败: ' + (error.message || '网络错误'))
  } finally {
    uploading.value = false
  }
}

const handleSelectFile = (id, checked) => {
  if (checked) {
    if (!selectedIds.value.includes(id)) {
      selectedIds.value.push(id)
    }
  } else {
    selectedIds.value = selectedIds.value.filter(i => i !== id)
  }
}

const handleSelectAll = (event) => {
  if (event.target.checked) {
    selectedIds.value = fileList.value.map(file => file.id)
  } else {
    selectedIds.value = []
  }
}

const handleRowClick = (id) => {
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

const handleDelete = async (id, fileName) => {
  if (!confirm(`确定要删除文件 "${fileName}" 吗？`)) {
    return
  }
  await deleteFiles([id])
}

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return
  if (!confirm(`确定要删除选中的 ${selectedIds.value.length} 个文件吗？`)) {
    return
  }
  await deleteFiles([...selectedIds.value])
}

const deleteFiles = async (ids) => {
  deleting.value = true
  try {
    const response = await knowledgeApi.deleteFiles(ids)
    if (response.data.code === 0) {
      alert(response.data.message || '删除成功')
      selectedIds.value = []
      fetchFileList()
    } else {
      alert('删除失败: ' + (response.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('删除文件错误:', error)
    alert('删除失败: ' + (error.message || '网络错误'))
  } finally {
    deleting.value = false
  }
}

const handleDownload = async (id) => {
  await downloadFiles([id])
}

const handleBatchDownload = async () => {
  if (selectedIds.value.length === 0) return
  await downloadFiles([...selectedIds.value])
}

const downloadFiles = async (ids) => {
  downloading.value = true
  try {
    // 获取要下载的文件信息
    const filesToDownload = fileList.value.filter(file => ids.includes(file.id))
    
    for (const file of filesToDownload) {
      try {
        // 优先使用新的文件流下载API
        const response = await knowledgeApi.downloadFile(file.id)
        
        // 处理blob下载
        if (response.data instanceof Blob) {
          const blob = response.data
          const url = window.URL.createObjectURL(blob)
          const link = document.createElement('a')
          link.href = url
          link.download = file.fileName || `file_${file.id}`
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link)
          window.URL.revokeObjectURL(url)
          
          // 添加延迟避免浏览器阻止多个下载
          await new Promise(resolve => setTimeout(resolve, 200))
        }
      } catch (apiError) {
        console.error(`API下载文件 ${file.id} 失败:`, apiError)
        
        // API失败时，尝试使用OSS URL作为备选方案
        if (file.url) {
          try {
            const link = document.createElement('a')
            link.href = file.url
            link.download = file.fileName || `file_${file.id}`
            link.target = '_blank'
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
            
            await new Promise(resolve => setTimeout(resolve, 200))
          } catch (urlError) {
            console.error(`URL下载文件 ${file.id} 也失败:`, urlError)
            alert(`下载文件 "${file.fileName}" 失败`)
          }
        } else {
          alert(`下载文件 "${file.fileName}" 失败: 无可用下载方式`)
        }
      }
    }
  } catch (error) {
    console.error('下载文件错误:', error)
    alert('下载失败: ' + (error.message || '网络错误'))
  } finally {
    downloading.value = false
  }
}

const goToPage = (page) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  fetchFileList()
}

const handlePageSizeChange = () => {
  currentPage.value = 1
  fetchFileList()
}

watch(pageInput, (newVal) => {
  if (newVal >= 1 && newVal <= totalPages.value) {
    currentPage.value = newVal
  }
})

onMounted(() => {
  chatStore.initialize()
  fetchFileList()
})
</script>

<style lang="scss" scoped>
.knowledge-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: var(--bg-primary);
  transition: background-color 0.3s ease;
}

.knowledge-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  min-width: 0;
}

.knowledge-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
  overflow: hidden;
}

// 顶部操作栏
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  background-color: var(--bg-secondary);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  
  .toolbar-left {
    display: flex;
    gap: 8px;
  }
  
  .toolbar-center {
    flex: 1;
    display: flex;
    justify-content: center;
  }
  
  .toolbar-right {
    display: flex;
    gap: 8px;
  }
  
  .upload-button {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    background-color: transparent;
    color: var(--text-primary);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
    transition: all 0.2s ease;
    
    &:hover:not(:disabled) {
      background-color: var(--bg-hover);
    }
    
    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }
  
  .search-box {
    position: relative;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background-color: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    width: 100%;
    max-width: 400px;
    
    svg {
      color: var(--text-secondary);
      flex-shrink: 0;
    }
    
    input {
      flex: 1;
      border: none;
      background: transparent;
      color: var(--text-primary);
      font-size: 14px;
      outline: none;
      
      &::placeholder {
        color: var(--text-secondary);
      }
    }
    
    .clear-search {
      background: transparent;
      border: none;
      color: var(--text-secondary);
      cursor: pointer;
      font-size: 20px;
      line-height: 1;
      padding: 0;
      width: 20px;
      height: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      
      &:hover {
        color: var(--text-primary);
      }
    }
  }
  
  .batch-button {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px;
    background-color: var(--bg-tertiary);
    color: var(--text-primary);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    cursor: pointer;
    font-size: 13px;
    transition: all 0.2s ease;
    
    &:hover:not(:disabled) {
      background-color: var(--bg-hover);
    }
    
    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    
    &.delete {
      color: #dc3545;
      
      &:hover:not(:disabled) {
        background-color: rgba(220, 53, 69, 0.1);
      }
    }
  }
}

// 表格容器
.table-container {
  flex: 1;
  overflow: auto;
  background-color: var(--bg-secondary);
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.file-table {
  width: 100%;
  border-collapse: collapse;
  
  thead {
    background-color: var(--bg-tertiary);
    position: sticky;
    top: 0;
    z-index: 10;
    
    th {
      padding: 12px 16px;
      text-align: left;
      font-size: 13px;
      font-weight: 600;
      color: var(--text-secondary);
      border-bottom: 1px solid var(--border-color);
    }
  }
  
  tbody {
    tr {
      border-bottom: 1px solid var(--border-color);
      transition: background-color 0.2s ease;
      
      &:hover {
        background-color: var(--bg-hover);
      }
      
      &.selected {
        background-color: var(--bg-active);
      }
      
      &.loading-row,
      &.empty-row {
        &:hover {
          background-color: transparent;
        }
      }
    }
    
    td {
      padding: 12px 16px;
      font-size: 14px;
      color: var(--text-primary);
    }
  }
  
  .checkbox-col {
    width: 50px;
    text-align: center;
    
    input[type="checkbox"] {
      cursor: pointer;
    }
  }
  
  .name-col {
    min-width: 200px;
    
    .file-name {
      cursor: pointer;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: 400px;
    }
  }
  
  .time-col {
    width: 180px;
    color: var(--text-secondary);
    font-size: 13px;
  }
  
  .action-col {
    width: 150px;
    
    .action-button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      margin-right: 8px;
      background-color: transparent;
      border: 1px solid var(--border-color);
      border-radius: 4px;
      color: var(--text-primary);
      cursor: pointer;
      transition: all 0.2s ease;
      
      &:hover:not(:disabled) {
        background-color: var(--bg-hover);
        border-color: var(--text-primary);
      }
      
      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      
      &.delete {
        color: #dc3545;
        
        &:hover:not(:disabled) {
          background-color: rgba(220, 53, 69, 0.1);
          border-color: #dc3545;
        }
      }
    }
  }
  
  .loading-cell,
  .empty-cell {
    text-align: center;
    padding: 48px 16px;
    
    .loading-spinner {
      color: var(--text-secondary);
    }
    
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      color: var(--text-secondary);
      
      svg {
        opacity: 0.5;
      }
      
      p {
        margin: 0;
        font-size: 14px;
      }
    }
  }
}

// 分页组件
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding: 12px 16px;
  background-color: var(--bg-secondary);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  
  .pagination-info {
    color: var(--text-secondary);
    font-size: 13px;
  }
  
  .pagination-controls {
    display: flex;
    align-items: center;
    gap: 8px;
    
    .page-button {
      padding: 6px 12px;
      background-color: var(--bg-primary);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      border-radius: 4px;
      cursor: pointer;
      font-size: 13px;
      transition: all 0.2s ease;
      
      &:hover:not(:disabled) {
        background-color: var(--bg-hover);
      }
      
      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
    
    .page-input {
      width: 60px;
      padding: 6px 8px;
      background-color: var(--bg-primary);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      border-radius: 4px;
      text-align: center;
      font-size: 13px;
      
      &:focus {
        outline: none;
        border-color: var(--accent-color);
      }
    }
    
    .page-size-select {
      padding: 6px 8px;
      background-color: var(--bg-primary);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      border-radius: 4px;
      cursor: pointer;
      font-size: 13px;
      
      &:focus {
        outline: none;
        border-color: var(--accent-color);
      }
    }
  }
}

// 响应式设计
@media (max-width: 768px) {
  .knowledge-content {
    padding: 12px;
  }
  
  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    
    .toolbar-left,
    .toolbar-center,
    .toolbar-right {
      width: 100%;
    }
    
    .toolbar-right {
      flex-direction: column;
    }
  }
  
  .file-table {
    font-size: 12px;
    
    th, td {
      padding: 8px;
    }
    
    .name-col .file-name {
      max-width: 150px;
    }
    
    .time-col {
      width: 120px;
      font-size: 11px;
    }
    
    .action-col {
      width: 100px;
    }
  }
  
  .pagination {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
    
    .pagination-controls {
      justify-content: center;
    }
  }
}
</style>

