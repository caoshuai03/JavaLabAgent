<template>
  <div :class="['message-item', `message-${message.sender}`]">
    <div class="message-container">
      <div class="message-content">
        <details 
          v-if="message.sender === 'assistant' && Array.isArray(message.toolEvents) && message.toolEvents.length > 0" 
          class="tool-events-panel"
          :open="detailsOpen"
          @toggle="onToggle"
        >
          <summary class="tool-events-summary">
            <div class="summary-left">
              <span class="thinking-text">思考过程</span>
            </div>
            <svg 
              class="summary-chevron" 
              xmlns="http://www.w3.org/2000/svg" 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2" 
              stroke-linecap="round" 
              stroke-linejoin="round"
            >
              <polyline points="9 18 15 12 9 6"></polyline>
            </svg>
          </summary>
          <div class="tool-events">
            <div v-for="(item, idx) in groupedToolEvents" :key="`group-${idx}`" class="tool-group-item">
              <!-- Case 1: Tool Interaction -->
              <div v-if="item.type === 'tool'" class="tool-interaction">
                <div class="tool-header">
                  <div class="tool-title-row">
                    <span class="tool-icon">🛠️</span>
                    <span class="tool-name">{{ item.call.payload.toolName }}</span>
                  </div>
                  <span class="tool-status" :class="{ success: item.result?.payload?.success, error: item.result && !item.result.payload.success }">
                    {{ item.result ? (item.result.payload.success ? '成功' : '失败') : '执行中...' }}
                  </span>
                </div>
                <div class="tool-details">
                  <div class="tool-detail-row">
                    <span class="tool-label">输入:</span>
                    <span class="tool-value">{{ prettyJson(item.call.payload.input) }}</span>
                  </div>
                  <div v-if="item.result" class="tool-detail-row">
                    <span class="tool-label">输出:</span>
                    <span class="tool-value">{{ prettyJson(item.result.payload.data || item.result.payload.error) }}</span>
                  </div>
                </div>
              </div>

              <!-- Case 2: Status -->
              <div v-else-if="item.type === 'status'" class="status-item">
                <span class="status-icon">💭</span>
                <span class="status-text">{{ formatStage(item.event.payload) }}</span>
              </div>
            </div>
          </div>
        </details>
        <div class="message-text" v-html="formatContent(message.content)" ref="messageTextRef" @click="handleCodeBlockClick"></div>

        <!-- 消息底部区域：操作按钮 + 时间 -->
        <div class="message-footer">
          <!-- 助手消息显示复制按钮和反馈按钮 -->
          <div v-if="message.sender === 'assistant'" class="message-actions">
            <button
              @click="handleCopy"
              :class="['action-button', { copied: copied }]"
              :title="copied ? '已复制' : '复制'"
            >
              <!-- 复制成功显示勾选图标，否则显示复制图标 -->
              <svg v-if="copied" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </button>
            <!-- 反馈按钮 -->
            <button
              @click="openFeedbackModal"
              class="action-button"
              title="反馈"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
              </svg>
            </button>
          </div>

          <!-- 反馈弹窗 -->
          <FeedbackModal
            v-if="showFeedbackModal"
            :message-content="message.content"
            :session-id="chatStore.currentConversationId"
            @close="closeFeedbackModal"
          />
          <!-- 时间显示 -->
          <div class="message-time">{{ formatTime(message.timestamp) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useChatStore } from '../stores/chat'
import { renderMarkdown } from '../utils/markdown'
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import FeedbackModal from './FeedbackModal.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const chatStore = useChatStore()
const messageTextRef = ref(null)
const copied = ref(false) // 复制成功状态
const showFeedbackModal = ref(false) // 反馈弹窗状态
const detailsOpen = ref(false) // 思考过程展开状态

// 监听展开状态变化
const onToggle = (event) => {
  detailsOpen.value = event.target.open
}

// 初始化和监听思考过程状态
const initThinkingState = () => {
  if (props.message.sender === 'assistant' && Array.isArray(props.message.toolEvents) && props.message.toolEvents.length > 0) {
    // 如果没有内容或者内容很短（正在思考或刚开始输出），则默认展开
    // 如果有较多内容（已经输出完毕或输出了一部分），则默认折叠
    const content = props.message.content || ''
    if (content.length < 5) {
      detailsOpen.value = true
    } else {
      detailsOpen.value = false
    }
  }
}

onMounted(() => {
  initThinkingState()
})

// 监听内容变化，实现"出字时折叠"
watch(() => props.message.content, (newVal, oldVal) => {
  if (props.message.sender === 'assistant' && Array.isArray(props.message.toolEvents) && props.message.toolEvents.length > 0) {
    const oldLen = oldVal ? oldVal.length : 0
    const newLen = newVal ? newVal.length : 0
    
    // 当内容从很少变为较多时（开始输出回答），自动折叠
    // 阈值设为 5 个字符，避免空字符串到第一个字的抖动
    if (oldLen < 5 && newLen >= 5 && detailsOpen.value) {
      detailsOpen.value = false
    }
  }
})

// 监听工具事件变化，实现"思考时展开"
watch(() => props.message.toolEvents, (newVal, oldVal) => {
  if (props.message.sender === 'assistant' && Array.isArray(newVal) && newVal.length > 0) {
    // 如果是新增的思考过程（之前没有），且内容为空，则展开
    const content = props.message.content || ''
    if ((!oldVal || oldVal.length === 0) && content.length < 5) {
      detailsOpen.value = true
    }
  }
}, { deep: true })

// 打开反馈弹窗
const openFeedbackModal = () => {
  showFeedbackModal.value = true
}

// 关闭反馈弹窗
const closeFeedbackModal = () => {
  showFeedbackModal.value = false
}

const formatContent = (content) => {
  if (!content) return ''

  // 只有助手消息使用 Markdown 渲染，用户消息保持原样
  if (props.message.sender === 'assistant') {
    return renderMarkdown(content)
  }

  // 用户消息：简单的文本格式化，将换行转换为 <br>
  return content
    .replace(/\n/g, '<br>')
    .replace(/ {2}/g, '&nbsp;&nbsp;')
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''

  const date = new Date(timestamp)
  const now = new Date()

  // 如果是今天，只显示时间
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  // 否则显示日期和时间
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const prettyJson = (obj) => {
  try {
    if (typeof obj === 'string') {
      // 尝试解析字符串
      const parsed = JSON.parse(obj)
      return JSON.stringify(parsed, null, 2)
    }
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return String(obj)
  }
}

const groupedToolEvents = computed(() => {
  if (!props.message.toolEvents || !props.message.toolEvents.length) return []
  
  const list = []
  // 使用 map 暂存正在进行的调用，以 round 为 key
  const activeCalls = new Map()

  props.message.toolEvents.forEach(e => {
    if (e.eventType === 'tool_call') {
      const round = e.payload?.round
      const callItem = {
        type: 'tool',
        call: e,
        result: null,
        round: round
      }
      activeCalls.set(round, callItem)
      list.push(callItem)
    } else if (e.eventType === 'tool_result') {
      const round = e.payload?.round
      const callItem = activeCalls.get(round)
      if (callItem) {
        callItem.result = e
        activeCalls.delete(round)
      }
    } else if (e.eventType === 'status') {
      const stage = e.payload?.stage
      // 过滤掉冗余状态，只展示关键节点
      if (stage === 'thinking' || stage === 'ready_to_answer' || stage === 'tool_running' || stage === 'tool_done') {
         list.push({ type: 'status', event: e })
      }
    }
  })

  return list
})

const formatStage = (payload) => {
  if (!payload || !payload.stage) return '进行中...'
  const map = {
    'thinking': '正在思考...',
    'ready_to_answer': '思考结束，开始回答',
    'tool_running': '正在使用工具...',
    'tool_done': '工具调用完成'
  }
  return map[payload.stage] || payload.stage
}

// 处理复制按钮点击 - 复制整条消息内容
const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.message.content)
    // 显示复制成功状态
    copied.value = true
    // 2秒后恢复原状态
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (err) {
    console.error('复制失败:', err)
    // 降级方案：使用旧的 execCommand
    const textArea = document.createElement('textarea')
    textArea.value = props.message.content
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
    // 显示复制成功状态
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  }
}

// 处理代码块点击事件（复制代码）
const handleCodeBlockClick = async (event) => {
  const copyButton = event.target.closest('.code-block-copy')
  if (copyButton) {
    const codeBlock = copyButton.closest('.code-block-wrapper')
    if (codeBlock) {
      const codeElement = codeBlock.querySelector('code')
      if (codeElement) {
        const codeText = codeElement.textContent || codeElement.innerText
        try {
          await navigator.clipboard.writeText(codeText)
          // 显示复制成功提示
          const originalText = copyButton.textContent
          copyButton.textContent = '已复制'
          copyButton.classList.add('copied')
          setTimeout(() => {
            copyButton.textContent = originalText
            copyButton.classList.remove('copied')
          }, 2000)
        } catch (err) {
          console.error('复制代码失败:', err)
        }
      }
    }
  }
}

// 为代码块添加复制按钮
const addCopyButtons = () => {
  if (!messageTextRef.value) return

  const codeBlocks = messageTextRef.value.querySelectorAll('.code-block-wrapper')
  codeBlocks.forEach((block) => {
    // 如果已经有复制按钮，跳过
    if (block.querySelector('.code-block-copy')) return

    const copyButton = document.createElement('button')
    copyButton.className = 'code-block-copy'
    copyButton.textContent = '复制'
    copyButton.title = '复制代码'

    const header = block.querySelector('.code-block-header')
    if (header) {
      header.appendChild(copyButton)
    }
  })
}

// 监听消息内容变化，添加复制按钮
onMounted(() => {
  nextTick(() => {
    addCopyButtons()
  })
})

// 监听消息内容变化（流式更新时）
watch(() => props.message.content, () => {
  nextTick(() => {
    addCopyButtons()
  })
}, { flush: 'post' })
</script>

<style lang="scss" scoped>
.message-item {
  padding: 24px 0;

  &.message-user {
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;

    .message-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      gap: 12px;
      justify-content: flex-end;

      .avatar {
        order: 2;
      }

      .message-content {
        order: 1;
        align-items: flex-end;

        .message-text {
          background-color: var(--user-message-bg);
          color: var(--user-message-text);
          white-space: pre-wrap;
        }
      }
    }

    @media (max-width: 768px) {
      padding: 16px 0;

      .message-container {
        padding: 0 16px;
      }
    }
  }

  &.message-assistant {
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;

    .message-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      gap: 12px;

      .message-content {
        align-items: flex-start;

        .message-text {
          background-color: var(--assistant-message-bg);
          color: var(--assistant-message-text);
        }
      }
    }

    @media (max-width: 768px) {
      padding: 16px 0;

      .message-container {
        padding: 0 16px;
      }
    }
  }
}

.message-container {
  display: flex;
}

.message-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0px; // 减小间距，让复制按钮更靠近文本
  min-width: 0;
}

.message-text {
  padding: 16px 20px;
  padding-bottom: 12px; // 减小底部内边距，让复制按钮更靠近文本
  border-radius: 12px;
  line-height: 1.75;
  font-size: 16px;
  word-wrap: break-word;
  white-space: normal;

  @media (max-width: 768px) {
    padding: 12px 16px;
    padding-bottom: 10px;
    font-size: 15px;
    border-radius: 10px;
  }

  // 移除最后一个子元素的底部 margin
  :deep(> *:last-child) {
    margin-bottom: 0 !important;
  }

  // Markdown 样式
  :deep(p) {
    margin: 0.25em 0;

    &:first-child {
      margin-top: 0;
    }

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(ul), :deep(ol) {
    margin: 0.5em 0;
    padding-left: 1.5em;
  }

  :deep(li) {
    margin: 0.25em 0;
  }

  :deep(blockquote) {
    margin: 1em 0;
    padding: 0.5em 1em;
    padding-left: 1em;
    border-left: 4px solid #10a37f;
    background-color: rgba(16, 163, 127, 0.05);
    border-radius: 4px;
    color: var(--text-secondary);
    font-style: italic;
  }

  // 行内代码样式 - ChatGPT 风格
  :deep(code:not(pre code)) {
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    background-color: rgba(175, 184, 193, 0.2);
    color: #d73a49;
  }

  // 代码块样式 - ChatGPT 风格（浅色主题）
  :deep(.code-block-wrapper) {
    margin: 1em 0;
    border-radius: 8px;
    overflow: hidden;
    background-color: #f6f8fa;
    border: 1px solid rgba(0, 0, 0, 0.1);
    position: relative;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);

    .code-block-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 16px;
      background-color: rgba(0, 0, 0, 0.03);
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);

      .code-block-lang {
        font-size: 12px;
        color: #656d76;
        font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', monospace;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        font-weight: 500;
      }

      .code-block-copy {
        padding: 4px 12px;
        font-size: 12px;
        background-color: transparent;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 6px;
        color: #656d76;
        cursor: pointer;
        transition: all 0.2s ease;
        font-family: inherit;

        &:hover {
          background-color: rgba(0, 0, 0, 0.05);
          border-color: rgba(0, 0, 0, 0.25);
          color: #24292f;
        }

        &.copied {
          background-color: #10a37f;
          border-color: #10a37f;
          color: #fff;
        }
      }
    }

    pre {
      margin: 0;
      padding: 16px;
      border-radius: 0;
      overflow-x: auto;
      background-color: transparent;

      code {
        padding: 0;
        background-color: transparent;
        font-size: 0.875em;
        line-height: 1.6;
        color: #24292f;
        font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
        display: block;
      }
    }
  }

  // 兼容旧的 pre 标签（没有包装器的情况）
  :deep(pre:not(.code-block-wrapper pre)) {
    margin: 1em 0;
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
    background-color: #f6f8fa;
    border: 1px solid rgba(0, 0, 0, 0.1);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);

    code {
      padding: 0;
      background-color: transparent;
      font-size: 0.875em;
      line-height: 1.6;
      color: #24292f;
      font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
    }
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 1em 0;
    width: 100%;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    overflow: hidden;

    th, td {
      padding: 12px 16px;
      border: 1px solid var(--border-color);
      text-align: left;
    }

    th {
      background-color: var(--bg-hover);
      font-weight: 600;
      color: var(--text-primary);
    }

    tr:nth-child(even) {
      background-color: var(--bg-secondary);
    }

    tr:hover {
      background-color: var(--bg-hover);
    }
  }

  :deep(a) {
    color: #10a37f;
    text-decoration: none;
    border-bottom: 1px solid transparent;
    transition: border-color 0.2s ease;

    &:hover {
      border-bottom-color: #10a37f;
    }
  }

  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    margin: 0.8em 0 0.4em 0;
    font-weight: 600;

    &:first-child {
      margin-top: 0;
    }
  }

  :deep(h1) { font-size: 1.5em; }
  :deep(h2) { font-size: 1.3em; }
  :deep(h3) { font-size: 1.1em; }
  :deep(h4) { font-size: 1em; }
  :deep(h5) { font-size: 0.9em; }
  :deep(h6) { font-size: 0.8em; }

  :deep(hr) {
    margin: 1em 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }
}

.tool-events-panel {
  margin-top: 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
  overflow: hidden;
  transition: all 0.2s ease;
}

.tool-events-summary {
  list-style: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  user-select: none;
  transition: background-color 0.2s;
}

.tool-events-summary:hover {
  background-color: var(--bg-hover);
}

.tool-events-summary::-webkit-details-marker {
  display: none;
}

.summary-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.thinking-text {
  font-weight: 500;
}

.summary-chevron {
  color: var(--text-secondary);
  transition: transform 0.2s ease;
  flex-shrink: 0;
  margin-left: 12px; /* 确保图标与文字有足够间距 */
}

.tool-events-panel[open] .summary-chevron {
  transform: rotate(90deg);
}

/* 移除原来的 ::after 伪元素 */
/*.tool-events-summary::after {
  content: '展开';
  color: var(--text-secondary);
  font-weight: 400;
  font-size: 12px;
}

.tool-events-panel[open] .tool-events-summary::after {
  content: '收起';
}*/

.tool-events {
  border-top: 1px solid var(--border-color);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-group-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--border-color);

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.tool-interaction {
  background-color: var(--bg-hover);
  border-radius: 6px;
  padding: 8px;
  border: 1px solid var(--border-color);
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}

.tool-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tool-icon {
  font-size: 14px;
}

.tool-name {
  font-weight: 600;
  color: var(--text-primary);
}

.tool-status {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: var(--bg-secondary);
  color: var(--text-secondary);

  &.success {
    background-color: rgba(16, 163, 127, 0.1);
    color: #10a37f;
  }
  
  &.error {
    background-color: rgba(211, 47, 47, 0.1);
    color: #d32f2f;
  }
}

.tool-details {
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: var(--bg-primary);
  padding: 6px;
  border-radius: 4px;
}

.tool-detail-row {
  display: flex;
  gap: 6px;
  overflow: hidden;
}

.tool-label {
  flex-shrink: 0;
  font-weight: 500;
  color: var(--text-secondary);
}

.tool-value {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: monospace;
  color: var(--text-primary);
  opacity: 0.9;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  padding: 4px 8px;
  font-style: italic;
}

.status-icon {
  font-size: 12px;
}

// 消息底部区域：复制按钮 + 时间
.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: -2px; // 负边距让内容更靠近文本
  padding-left: 20px; // 与 .message-text 的左内边距对齐
}

// 操作按钮区域 - ChatGPT 风格
.message-actions {
  display: flex;
  gap: 4px;

  // ChatGPT 风格的操作按钮
  .action-button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    padding: 0;
    background: transparent;
    border: none;
    border-radius: 6px;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all 0.15s ease;

    svg {
      width: 18px;
      height: 18px;
    }

    &:hover {
      background-color: var(--bg-hover);
      color: var(--text-primary);
    }

    // 复制成功时的状态
    &.copied {
      color: #10a37f;
    }
  }
}

.message-time {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 28px; // 与按钮高度对齐
}
</style>
