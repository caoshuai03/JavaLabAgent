<template>
  <div class="mcp-settings-page">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack" title="返回">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
        </button>
        <h1 class="page-title">MCP 工具管理</h1>
      </div>
      <div class="header-right">
        <span class="tool-count">共 {{ totalTools }} 个工具</span>
        <button class="add-btn" @click="showAddDialog = true">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
          添加服务器
        </button>
      </div>
    </div>

    <!-- 说明区域 -->
    <div class="info-banner">
      <div class="info-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
      </div>
      <p>通过 MCP (Model Context Protocol) 协议连接外部工具服务器，让 AI 助手获得更多能力。支持 <strong>stdio</strong>（本地子进程）和 <strong>http</strong>（远程服务）两种连接方式。</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <span>加载中...</span>
    </div>

    <!-- 空状态 -->
    <div v-else-if="servers.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"></path></svg>
      </div>
      <h3>尚未配置 MCP 服务器</h3>
      <p>点击「添加服务器」开始配置外部工具</p>
      <button class="add-btn" @click="showAddDialog = true">添加服务器</button>
    </div>

    <!-- 服务器列表 -->
    <div v-else class="server-list">
      <div
        v-for="server in servers"
        :key="server.name"
        :class="['server-card', { disabled: !server.enabled }]"
      >
        <div class="server-header">
          <div class="server-info">
            <div class="server-name-row">
              <span :class="['status-dot', server.enabled ? 'active' : 'inactive']"></span>
              <h3 class="server-name">{{ server.name }}</h3>
              <span class="server-type-badge">{{ server.type }}</span>
            </div>
            <p class="server-desc" v-if="server.description">{{ server.description }}</p>
            <div class="server-meta">
              <span v-if="server.type === 'stdio'" class="meta-item">
                <code>{{ server.command }} {{ (server.args || []).join(' ') }}</code>
              </span>
              <span v-if="server.type === 'http'" class="meta-item">
                <code>{{ server.url }}</code>
              </span>
              <span class="meta-item tool-badge" v-if="server.toolCount > 0">
                {{ server.toolCount }} 个工具
              </span>
            </div>
            <!-- 工具名称标签 -->
            <div class="tool-tags" v-if="server.toolNames && server.toolNames.length > 0">
              <span
                v-for="toolName in server.toolNames"
                :key="toolName"
                class="tool-tag"
              >{{ toolName }}</span>
            </div>
          </div>
          <div class="server-actions">
            <button
              @click="handleToggle(server)"
              :class="['action-btn', server.enabled ? 'warn' : 'success']"
              :title="server.enabled ? '禁用' : '启用'"
            >
              {{ server.enabled ? '禁用' : '启用' }}
            </button>
            <button
              @click="handleTest(server.name)"
              class="action-btn"
              :disabled="!server.enabled || testingServer === server.name"
              title="测试连接"
            >
              <span v-if="testingServer === server.name" class="btn-spinner"></span>
              <span v-else>测试</span>
            </button>
            <button
              @click="handleRefresh(server.name)"
              class="action-btn"
              :disabled="!server.enabled"
              title="刷新工具列表"
            >
              刷新
            </button>
            <button
              @click="handleRestart(server.name)"
              class="action-btn"
              :disabled="!server.enabled"
              title="重启服务器"
            >
              重启
            </button>
            <button
              @click="confirmDelete(server.name)"
              class="action-btn danger"
              title="删除"
            >
              删除
            </button>
          </div>
        </div>
        <!-- 测试结果 -->
        <div v-if="testResults[server.name]" :class="['test-result', testResults[server.name].success ? 'success' : 'error']">
          {{ testResults[server.name].message }}
        </div>
      </div>
    </div>

    <!-- 添加服务器对话框 -->
    <div v-if="showAddDialog" class="modal-overlay" @click.self="showAddDialog = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2>添加 MCP 服务器</h2>
          <button class="close-btn" @click="showAddDialog = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>服务器名称 <span class="required">*</span></label>
            <input
              v-model="newServer.name"
              type="text"
              placeholder="例如: weather-server"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label>连接类型</label>
            <div class="radio-group">
              <label class="radio-label">
                <input type="radio" v-model="newServer.type" value="stdio" />
                <span>stdio（本地子进程）</span>
              </label>
              <label class="radio-label">
                <input type="radio" v-model="newServer.type" value="http" />
                <span>http（远程服务）</span>
              </label>
            </div>
          </div>

          <!-- stdio 模式字段 -->
          <template v-if="newServer.type === 'stdio'">
            <div class="form-group">
              <label>命令 <span class="required">*</span></label>
              <input
                v-model="newServer.command"
                type="text"
                placeholder="例如: npx, python, node"
                class="form-input"
              />
            </div>
            <div class="form-group">
              <label>参数（每行一个）</label>
              <textarea
                v-model="newServer.argsText"
                placeholder="例如:&#10;-y&#10;@modelcontextprotocol/server-weather"
                class="form-textarea"
                rows="3"
              ></textarea>
            </div>
            <div class="form-group">
              <label>环境变量（每行 KEY=VALUE）</label>
              <textarea
                v-model="newServer.envText"
                placeholder="例如:&#10;API_KEY=your-key&#10;DEBUG=true"
                class="form-textarea"
                rows="2"
              ></textarea>
            </div>
          </template>

          <!-- http 模式字段 -->
          <template v-if="newServer.type === 'http'">
            <div class="form-group">
              <label>服务器 URL <span class="required">*</span></label>
              <input
                v-model="newServer.url"
                type="text"
                placeholder="例如: http://localhost:3001"
                class="form-input"
              />
            </div>
          </template>

          <div class="form-group">
            <label>描述</label>
            <input
              v-model="newServer.description"
              type="text"
              placeholder="服务器用途描述"
              class="form-input"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button class="cancel-btn" @click="showAddDialog = false">取消</button>
          <button
            class="submit-btn"
            @click="handleAdd"
            :disabled="!canAdd || adding"
          >
            {{ adding ? '添加中...' : '添加' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Toast 提示 -->
    <div v-if="toast.show" :class="['toast', toast.type]">
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  getMcpServers,
  addMcpServer,
  removeMcpServer,
  updateMcpServer,
  testMcpServer,
  restartMcpServer,
  refreshMcpServerTools
} from '../api/mcp'

const router = useRouter()

// ==================== 状态 ====================
const loading = ref(false)
const servers = ref([])
const totalTools = ref(0)
const showAddDialog = ref(false)
const adding = ref(false)
const testingServer = ref(null)
const testResults = ref({})

// 新增服务器表单
const newServer = ref({
  name: '',
  type: 'stdio',
  command: '',
  argsText: '',
  envText: '',
  url: '',
  description: ''
})

// Toast 提示
const toast = ref({ show: false, message: '', type: 'info' })

// ==================== 计算属性 ====================
const canAdd = computed(() => {
  if (!newServer.value.name.trim()) return false
  if (newServer.value.type === 'stdio' && !newServer.value.command.trim()) return false
  if (newServer.value.type === 'http' && !newServer.value.url.trim()) return false
  return true
})

// ==================== 方法 ====================
const goBack = () => {
  router.push('/')
}

/** 加载服务器列表 */
const loadServers = async () => {
  loading.value = true
  try {
    const response = await getMcpServers()
    servers.value = response.data.servers || []
    totalTools.value = response.data.totalTools || 0
  } catch (error) {
    console.error('加载MCP服务器列表失败:', error)
    showToast('加载失败: ' + (error.message || '网络错误'), 'error')
  } finally {
    loading.value = false
  }
}

/** 添加服务器 */
const handleAdd = async () => {
  if (!canAdd.value) return
  adding.value = true
  try {
    const data = {
      name: newServer.value.name.trim(),
      type: newServer.value.type,
      enabled: true,
      description: newServer.value.description.trim() || undefined
    }
    if (newServer.value.type === 'stdio') {
      data.command = newServer.value.command.trim()
      // 解析参数（按行分割）
      if (newServer.value.argsText.trim()) {
        data.args = newServer.value.argsText.trim().split('\n').map(s => s.trim()).filter(Boolean)
      }
      // 解析环境变量
      if (newServer.value.envText.trim()) {
        const env = {}
        newServer.value.envText.trim().split('\n').forEach(line => {
          const idx = line.indexOf('=')
          if (idx > 0) {
            env[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
          }
        })
        data.env = env
      }
    } else {
      data.url = newServer.value.url.trim()
    }

    const response = await addMcpServer(data)
    if (response.data.success) {
      showToast('添加成功', 'success')
      showAddDialog.value = false
      resetNewServer()
      await loadServers()
    } else {
      showToast(response.data.message || '添加失败', 'error')
    }
  } catch (error) {
    showToast('添加失败: ' + (error.message || '网络错误'), 'error')
  } finally {
    adding.value = false
  }
}

/** 切换启用/禁用 */
const handleToggle = async (server) => {
  try {
    const response = await updateMcpServer(server.name, { enabled: !server.enabled })
    if (response.data.success) {
      showToast(server.enabled ? '已禁用' : '已启用', 'success')
      await loadServers()
    } else {
      showToast(response.data.message || '操作失败', 'error')
    }
  } catch (error) {
    showToast('操作失败', 'error')
  }
}

/** 测试连接 */
const handleTest = async (name) => {
  testingServer.value = name
  testResults.value[name] = null
  try {
    const response = await testMcpServer(name)
    testResults.value[name] = {
      success: response.data.success,
      message: response.data.message
    }
  } catch (error) {
    testResults.value[name] = {
      success: false,
      message: '测试失败: ' + (error.message || '网络错误')
    }
  } finally {
    testingServer.value = null
    // 3秒后清除测试结果
    setTimeout(() => {
      testResults.value[name] = null
    }, 5000)
  }
}

/** 刷新工具列表 */
const handleRefresh = async (name) => {
  try {
    const response = await refreshMcpServerTools(name)
    if (response.data.success) {
      showToast(response.data.message, 'success')
      await loadServers()
    } else {
      showToast(response.data.message || '刷新失败', 'error')
    }
  } catch (error) {
    showToast('刷新失败', 'error')
  }
}

/** 重启服务器 */
const handleRestart = async (name) => {
  try {
    const response = await restartMcpServer(name)
    if (response.data.success) {
      showToast('重启成功', 'success')
      await loadServers()
    } else {
      showToast(response.data.message || '重启失败', 'error')
    }
  } catch (error) {
    showToast('重启失败', 'error')
  }
}

/** 确认删除 */
const confirmDelete = async (name) => {
  if (!confirm(`确定要删除 MCP 服务器「${name}」吗？`)) return
  try {
    const response = await removeMcpServer(name)
    if (response.data.success) {
      showToast('删除成功', 'success')
      await loadServers()
    } else {
      showToast(response.data.message || '删除失败', 'error')
    }
  } catch (error) {
    showToast('删除失败', 'error')
  }
}

/** 重置表单 */
const resetNewServer = () => {
  newServer.value = {
    name: '',
    type: 'stdio',
    command: '',
    argsText: '',
    envText: '',
    url: '',
    description: ''
  }
}

/** 显示 Toast */
const showToast = (message, type = 'info') => {
  toast.value = { show: true, message, type }
  setTimeout(() => {
    toast.value.show = false
  }, 3000)
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadServers()
})
</script>

<style lang="scss" scoped>
.mcp-settings-page {
  min-height: 100vh;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  padding: 0;
}

// ==================== 顶部标题栏 ====================
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  border-bottom: 1px solid var(--border-color);
  background-color: var(--bg-secondary);
  position: sticky;
  top: 0;
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: var(--text-primary);
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover {
      background-color: var(--bg-hover);
    }
  }

  .page-title {
    font-size: 18px;
    font-weight: 600;
    margin: 0;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .tool-count {
    font-size: 13px;
    color: var(--text-secondary);
  }
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background-color: #10a37f;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #0d8c6d;
  }
}

// ==================== 说明区域 ====================
.info-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 24px 32px 0;
  padding: 12px 16px;
  background-color: rgba(16, 163, 127, 0.06);
  border: 1px solid rgba(16, 163, 127, 0.15);
  border-radius: 10px;

  .info-icon {
    color: #10a37f;
    flex-shrink: 0;
    margin-top: 2px;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
  }
}

// ==================== 加载 & 空状态 ====================
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--text-secondary);

  .spinner {
    width: 32px;
    height: 32px;
    border: 3px solid var(--border-color);
    border-top-color: #10a37f;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 0;
  color: var(--text-secondary);

  .empty-icon {
    color: var(--text-secondary);
    opacity: 0.4;
  }

  h3 {
    margin: 0;
    font-size: 16px;
    color: var(--text-primary);
  }

  p {
    margin: 0;
    font-size: 14px;
  }
}

// ==================== 服务器列表 ====================
.server-list {
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.server-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  background-color: var(--bg-secondary);
  transition: all 0.2s;

  &:hover {
    border-color: rgba(16, 163, 127, 0.3);
  }

  &.disabled {
    opacity: 0.6;
  }
}

.server-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;

  @media (max-width: 768px) {
    flex-direction: column;
  }
}

.server-info {
  flex: 1;
  min-width: 0;
}

.server-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.active {
    background-color: #10a37f;
    box-shadow: 0 0 4px rgba(16, 163, 127, 0.4);
  }

  &.inactive {
    background-color: #9ca3af;
  }
}

.server-name {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}

.server-type-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: rgba(16, 163, 127, 0.1);
  color: #10a37f;
  font-weight: 500;
  text-transform: uppercase;
}

.server-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.server-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;

  .meta-item {
    font-size: 12px;
    color: var(--text-secondary);

    code {
      padding: 2px 6px;
      border-radius: 4px;
      background-color: rgba(175, 184, 193, 0.15);
      font-family: 'SFMono-Regular', 'Consolas', monospace;
      font-size: 12px;
    }
  }

  .tool-badge {
    padding: 2px 8px;
    border-radius: 4px;
    background-color: rgba(16, 163, 127, 0.08);
    color: #10a37f;
    font-weight: 500;
  }
}

.tool-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.tool-tag {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  background-color: rgba(175, 184, 193, 0.12);
  color: var(--text-secondary);
  font-family: 'SFMono-Regular', 'Consolas', monospace;
}

// ==================== 操作按钮 ====================
.server-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover:not(:disabled) {
    background-color: var(--bg-hover);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &.danger {
    color: #dc3545;
    border-color: rgba(220, 53, 69, 0.3);

    &:hover:not(:disabled) {
      background-color: rgba(220, 53, 69, 0.08);
    }
  }

  &.warn {
    color: #f59e0b;
    border-color: rgba(245, 158, 11, 0.3);
  }

  &.success {
    color: #10a37f;
    border-color: rgba(16, 163, 127, 0.3);
  }
}

.btn-spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid var(--border-color);
  border-top-color: var(--text-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

// ==================== 测试结果 ====================
.test-result {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;

  &.success {
    background-color: rgba(16, 163, 127, 0.08);
    color: #10a37f;
    border: 1px solid rgba(16, 163, 127, 0.2);
  }

  &.error {
    background-color: rgba(220, 53, 69, 0.06);
    color: #dc3545;
    border: 1px solid rgba(220, 53, 69, 0.15);
  }
}

// ==================== 弹窗 ====================
.modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 520px;
  max-width: 90vw;
  max-height: 85vh;
  background-color: var(--bg-primary);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-color);

  h2 {
    margin: 0;
    font-size: 17px;
    font-weight: 600;
  }

  .close-btn {
    width: 32px;
    height: 32px;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 20px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      background-color: var(--bg-hover);
    }
  }
}

.modal-body {
  padding: 20px 24px;
  overflow-y: auto;
}

.form-group {
  margin-bottom: 16px;

  label {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: var(--text-primary);
    margin-bottom: 6px;
  }

  .required {
    color: #dc3545;
  }
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;

  &:focus {
    border-color: #10a37f;
  }

  &::placeholder {
    color: var(--text-secondary);
    opacity: 0.6;
  }
}

.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 13px;
  font-family: 'SFMono-Regular', 'Consolas', monospace;
  outline: none;
  resize: vertical;
  transition: border-color 0.2s;
  box-sizing: border-box;

  &:focus {
    border-color: #10a37f;
  }

  &::placeholder {
    color: var(--text-secondary);
    opacity: 0.6;
  }
}

.radio-group {
  display: flex;
  gap: 20px;
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  cursor: pointer;

  input[type="radio"] {
    accent-color: #10a37f;
  }
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--border-color);
}

.cancel-btn {
  padding: 8px 20px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  cursor: pointer;

  &:hover {
    background-color: var(--bg-hover);
  }
}

.submit-btn {
  padding: 8px 24px;
  border: none;
  border-radius: 8px;
  background-color: #10a37f;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover:not(:disabled) {
    background-color: #0d8c6d;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

// ==================== Toast ====================
.toast {
  position: fixed;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  z-index: 2000;
  animation: fadeInUp 0.3s ease;

  &.success {
    background-color: #10a37f;
    color: #fff;
  }

  &.error {
    background-color: #dc3545;
    color: #fff;
  }

  &.info {
    background-color: var(--bg-secondary);
    color: var(--text-primary);
    border: 1px solid var(--border-color);
  }
}

// ==================== 动画 ====================
@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

// ==================== 响应式 ====================
@media (max-width: 768px) {
  .page-header {
    padding: 12px 16px;
  }

  .info-banner {
    margin: 16px 16px 0;
  }

  .server-list {
    padding: 16px;
  }

  .server-actions {
    flex-wrap: wrap;
  }
}
</style>
