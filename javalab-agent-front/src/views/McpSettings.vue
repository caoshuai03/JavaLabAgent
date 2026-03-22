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
            </div>
            <p class="server-desc" v-if="server.description">{{ server.description }}</p>
            <!-- 工具名称标签（悬停显示描述） -->
            <div class="tool-tags" v-if="server.tools && server.tools.length > 0">
              <span
                v-for="tool in server.tools"
                :key="tool.name"
                class="tool-tag"
                @mouseenter="showToolTip($event, tool.description)"
                @mouseleave="hideToolTip"
              >{{ tool.name }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>


    <!-- 工具描述 Tooltip -->
    <div
      v-if="tooltip.visible"
      class="tool-tooltip"
      :style="{ top: tooltip.y + 'px', left: tooltip.x + 'px' }"
    >
      {{ tooltip.text }}
    </div>

    <!-- Toast 提示 -->
    <div v-if="toast.show" :class="['toast', toast.type]">
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMcpServers } from '../api/mcp'

const router = useRouter()

// ==================== 状态 ====================
const loading = ref(false)
const servers = ref([])

// Toast 提示
const toast = ref({ show: false, message: '', type: 'info' })

// 工具描述 Tooltip 状态
const tooltip = ref({ visible: false, text: '', x: 0, y: 0 })

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
  } catch (error) {
    console.error('加载MCP服务器列表失败:', error)
    showToast('加载失败: ' + (error.message || '网络错误'), 'error')
  } finally {
    loading.value = false
  }
}

/** 鼠标移入工具标签时显示描述 Tooltip */
const showToolTip = (event, description) => {
  if (!description) return
  const rect = event.target.getBoundingClientRect()
  const tooltipMaxWidth = 320
  const padding = 12 // 距离视口边缘的最小距离
  // 居中对齐标签，同时限制不超出视口左右边界
  let x = rect.left + rect.width / 2
  x = Math.max(padding + tooltipMaxWidth / 2, x)
  x = Math.min(window.innerWidth - padding - tooltipMaxWidth / 2, x)
  tooltip.value = {
    visible: true,
    text: description,
    x,
    y: rect.top - 8 // tooltip 显示在标签上方
  }
}

/** 鼠标移出时隐藏 Tooltip */
const hideToolTip = () => {
  tooltip.value.visible = false
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
  cursor: default;
  transition: background-color 0.2s, color 0.2s;

  &:hover {
    background-color: rgba(16, 163, 127, 0.12);
    color: #10a37f;
  }
}

// ==================== 工具描述 Tooltip ====================
.tool-tooltip {
  position: fixed;
  transform: translateX(-50%) translateY(-100%);
  max-width: 320px;
  padding: 8px 14px;
  border-radius: 8px;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.5;
  border: 1px solid var(--border-color);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  pointer-events: none;
  z-index: 3000;
  animation: tooltipFadeIn 0.15s ease;
  word-break: break-word;
}

@keyframes tooltipFadeIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-100%) translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(-100%);
  }
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
