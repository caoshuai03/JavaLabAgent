<template>
  <div :class="['message-item', `message-${message.sender}`]">
    <div class="message-container">
      <div class="message-content">
        <!-- 思考过程与工具调用面板 -->
        <div v-if="message.sender === 'assistant' && toolCalls.length > 0" class="tool-calls-panel">
          <div class="tool-calls-list">
            <div v-for="(item, idx) in toolCalls" :key="`tool-${idx}`" class="tool-call-item">
              <!-- 左侧连接线 -->
              <div class="tool-call-line" v-if="idx !== toolCalls.length - 1"></div>

              <div class="tool-call-content">
                <div class="tool-icon-wrapper">
                  <span
                    class="tool-icon"
                    v-html="
                      getToolIconSvg(
                        item.type === 'skill'
                          ? 'skill:' + item.call.payload.skillName
                          : item.call.payload.toolName,
                      )
                    "
                  ></span>
                </div>
                <div class="tool-text">
                  <!-- 根据工具类型显示不同的文字格式 -->
                  <span class="tool-name">
                    <template v-if="item.type === 'skill'">
                      调用 Skill：{{ item.call.payload.skillName }}
                    </template>
                    <template v-else-if="item.type === 'tool' && item.call.payload.toolName && item.call.payload.toolName.includes('mcp:')">
                      调用 MCP：{{ getToolDisplayName(item.call.payload.toolName) }}
                    </template>
                    <template v-else>
                      调用 Tool：{{ getToolDisplayName(item.call.payload.toolName) }}
                    </template>
                  </span>
                  <span class="tool-divider" v-if="getToolSummary(item)">|</span>
                  <span
                    class="tool-summary"
                    v-if="getToolSummary(item)"
                    v-tooltip="getToolSummary(item)"
                    >{{ truncateText(getToolSummary(item)) }}</span
                  >
                </div>
                <!-- 移除右侧的状态图标 -->
              </div>
            </div>
          </div>
        </div>
        <div
          class="message-text"
          v-html="formatContent(message.content)"
          ref="messageTextRef"
          @click="handleCodeBlockClick"
        ></div>

        <!-- 消息底部区域：操作按钮 + 时间 -->
        <div class="message-footer">
          <!-- 助手消息显示复制按钮和反馈按钮 -->
          <div v-if="message.sender === 'assistant'" class="message-actions">
            <button
              @click="handleCopy"
              :class="['action-button', { copied: copied }]"
              v-tooltip="copied ? '已复制' : '复制'"
            >
              <!-- 复制成功显示勾选图标，否则显示复制图标 -->
              <svg
                v-if="copied"
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <svg
                v-else
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </button>
            <!-- 反馈按钮 -->
            <button @click="openFeedbackModal" class="action-button" v-tooltip="'反馈'">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
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
    required: true,
  },
})

const chatStore = useChatStore()
const messageTextRef = ref(null)
const copied = ref(false) // 复制成功状态
const showFeedbackModal = ref(false) // 反馈弹窗状态

// 打开反馈弹窗
const openFeedbackModal = () => {
  showFeedbackModal.value = true
}

// 关闭反馈弹窗
const closeFeedbackModal = () => {
  showFeedbackModal.value = false
}

/** 截断文本为原来的一半长度 */
const truncateText = (text, maxLen = 20) => {
  if (!text) return ''
  if (text.length <= maxLen) return text
  return text.substring(0, maxLen) + '...'
}

const formatContent = (content) => {
  if (!content) return ''

  // 只有助手消息使用 Markdown 渲染，用户消息保持原样
  if (props.message.sender === 'assistant') {
    return renderMarkdown(content)
  }

  // 用户消息：简单的文本格式化，将换行转换为 <br>
  return content.replace(/\n/g, '<br>').replace(/ {2}/g, '&nbsp;&nbsp;')
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

const toolCalls = computed(() => {
  if (!props.message.toolEvents || !props.message.toolEvents.length) return []

  const list = []
  // 使用 map 暂存正在进行的调用，以 round 为 key
  const activeCalls = new Map()

  props.message.toolEvents.forEach((e) => {
    // 处理 Skill 激活事件
    if (e.eventType === 'skill_loaded') {
      const skills = e.payload?.skills || []
      skills.forEach((skill) => {
        list.push({
          type: 'skill',
          call: {
            eventType: 'skill_loaded',
            payload: {
              skillName: skill.name,
              description: skill.description,
              triggerKeywords: skill.triggerKeywords,
            },
            ts: e.ts,
          },
          result: { success: true }, // Skills 加载完成即为成功
        })
      })
    } else if (e.eventType === 'tool_call') {
      const round = e.payload?.round
      const callItem = {
        type: 'tool',
        call: e,
        result: null,
        round: round,
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
    }
  })

  return list
})

const getToolIconSvg = (toolName) => {
  // Skills 使用书本图标
  if (toolName && toolName.startsWith('skill:')) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>`
  }
  if (toolName === 'knowledge_search' || toolName === 'web_search') {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>`
  }
  if (toolName === 'read_file' || toolName === 'write_file') {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>`
  }
  if (toolName === 'run_command') {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>`
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"></path></svg>`
}

const getToolDisplayName = (toolName) => {
  // Skills 特殊处理
  if (toolName && toolName.startsWith('skill:')) {
    return toolName.substring(6) // 移除 'skill:' 前缀
  }
  // 内置工具名称映射
  const builtinNames = {
    knowledge_search: '知识检索',
    web_search: '搜索网页',
    read_file: '阅读',
    write_file: '写入',
    run_command: '运行命令',
    session_recall: '会话回顾',
    current_time: '获取时间',
    calculator: '计算器',
  }
  if (builtinNames[toolName]) return builtinNames[toolName]
  // MCP外部工具：直接显示工具名
  return toolName || '调用工具'
}

const getToolSummary = (item) => {
  try {
    // Skills 显示描述
    if (item.type === 'skill') {
      return item.call.payload.description || ''
    }
    // 优先使用后端返回的工具描述
    if (item.call.payload.description) {
      return item.call.payload.description
    }
    // 内置工具描述兜底映射
    const toolName = item.call.payload.toolName
    const builtinDescriptions = {
      knowledge_search: '查询知识库',
      web_search: '搜索网页',
      read_file: '读取文件内容',
      write_file: '写入文件内容',
      run_command: '执行终端命令',
      session_recall: '回顾当前会话消息',
      current_time: '获取当前时间',
      calculator: '计算数学表达式',
    }
    return builtinDescriptions[toolName] || ''
  } catch (e) {
    // ignore
  }
  return ''
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
          copyButton.classList.add('copied')
          copyButton.setAttribute('data-tooltip', '已复制')
          
          // Re-render tooltip if it's currently showing
          if (copyButton._tooltipEl) {
            copyButton._tooltipEl.textContent = '已复制'
          }
          
          setTimeout(() => {
            copyButton.classList.remove('copied')
            copyButton.setAttribute('data-tooltip', '复制代码')
            if (copyButton._tooltipEl) {
              copyButton._tooltipEl.textContent = '复制代码'
            }
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
    copyButton.innerHTML = `
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
      </svg>
    `
    // Add tooltip via data attribute instead of v-tooltip directive since it's dynamically created DOM
    copyButton.setAttribute('data-tooltip', '复制代码')

    // Add event listeners for custom tooltip behavior
    copyButton.addEventListener('mouseenter', () => {
      const tooltipText = copyButton.getAttribute('data-tooltip')
      if (!tooltipText) return

      // We simulate v-tooltip logic for dynamically created elements
      let tooltipTimer = setTimeout(() => {
        const tooltipEl = document.createElement('div')
        tooltipEl.className = 'global-tooltip'
        tooltipEl.textContent = tooltipText
        document.body.appendChild(tooltipEl)

        const rect = copyButton.getBoundingClientRect()
        const padding = 12
        const estimatedWidth = tooltipText.length * 13 + 28
        const estimatedHalfWidth = estimatedWidth / 2

        let x = rect.left + rect.width / 2
        x = Math.max(padding + estimatedHalfWidth, x)
        x = Math.min(window.innerWidth - padding - estimatedHalfWidth, x)

        let y = rect.top - 8
        tooltipEl.style.top = `${y}px`
        tooltipEl.style.left = `${x}px`

        requestAnimationFrame(() => {
          const tooltipRect = tooltipEl.getBoundingClientRect()
          if (tooltipRect.top < padding) {
            tooltipEl.style.top = `${rect.bottom + 8}px`
          }
        })

        copyButton._tooltipEl = tooltipEl
      }, 400)

      copyButton._tooltipTimer = tooltipTimer
    })

    copyButton.addEventListener('mouseleave', () => {
      if (copyButton._tooltipTimer) clearTimeout(copyButton._tooltipTimer)
      if (copyButton._tooltipEl) {
        copyButton._tooltipEl.remove()
        copyButton._tooltipEl = null
      }
    })

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
watch(
  () => props.message.content,
  () => {
    nextTick(() => {
      addCopyButtons()
    })
  },
  { flush: 'post' },
)
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
  padding: 10px 16px;
  padding-bottom: 8px; // 减小底部内边距，让复制按钮更靠近文本
  border-radius: 12px;
  line-height: 1.6;
  font-size: 15px;
  word-wrap: break-word;
  white-space: normal;

  @media (max-width: 768px) {
    padding: 8px 12px;
    padding-bottom: 6px;
    font-size: 14px;
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

  :deep(ul),
  :deep(ol) {
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
    border-left: 4px solid #90138b;
    background-color: rgba(144, 19, 139, 0.05);
    border-radius: 4px;
    color: var(--text-secondary);
    font-style: italic;
  }

  // 行内代码样式 - ChatGPT 风格
  :deep(code:not(pre code)) {
    padding: 2px 6px;
    border-radius: 4px;
    font-family:
      'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
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
        padding: 6px;
        background-color: transparent;
        border: none;
        color: var(--text-tertiary);
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.2s;
        border-radius: 6px;
        
        svg {
          width: 16px;
          height: 16px;
        }

        &:hover {
          color: var(--text-primary);
          background-color: var(--bg-hover, rgba(0, 0, 0, 0.05));
        }

        &.copied {
          color: #90138b;
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
        font-family:
          'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New',
          monospace;
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
      font-family:
        'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
    }
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 1em 0;
    width: 100%;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    overflow: hidden;

    th,
    td {
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
    color: #90138b;
    text-decoration: none;
    border-bottom: 1px solid transparent;
    transition: border-color 0.2s ease;

    &:hover {
      border-bottom-color: #90138b;
    }
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 0.8em 0 0.4em 0;
    font-weight: 600;

    &:first-child {
      margin-top: 0;
    }
  }

  :deep(h1) {
    font-size: 1.5em;
  }
  :deep(h2) {
    font-size: 1.3em;
  }
  :deep(h3) {
    font-size: 1.1em;
  }
  :deep(h4) {
    font-size: 1em;
  }
  :deep(h5) {
    font-size: 0.9em;
  }
  :deep(h6) {
    font-size: 0.8em;
  }

  :deep(hr) {
    margin: 1em 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }
}

.tool-calls-panel {
  margin: 0 0 16px 16px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 12px;
  background: transparent;
  overflow: hidden;
  max-width: 600px;

  @media (max-width: 768px) {
    margin: 0 0 12px 0;
    max-width: 100%;
  }
}

.tool-calls-list {
  padding: 12px 16px 12px 11px; // 左侧内边距改为 11px，使 24px 宽的图标中心对齐 16px 的文本
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tool-call-item {
  position: relative;
}

.tool-call-line {
  position: absolute;
  left: 11.5px; // 修改连接线位置，使其在 24px 宽的图标中心
  top: 24px;
  bottom: -20px;
  width: 1px;
  border-left: 1px dashed var(--border-color, #d1d5db);
  z-index: 1;
}

.tool-call-content {
  display: flex;
  align-items: center;
  position: relative;
  z-index: 2;
  cursor: default;
}

.tool-icon-wrapper {
  width: 24px;
  height: 24px;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 8px; // 减少与文字的距离
}

.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #6b7280);
}

.tool-text {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  white-space: nowrap;
}

.tool-name {
  font-size: 14px;
  color: var(--text-primary, #374151);
  font-weight: 500;
  flex-shrink: 0;
  font-family: inherit; // 继承字体，保持中英文和谐
  line-height: 1.4; // 调整行高
}

.tool-divider {
  color: var(--border-color, #d1d5db);
  font-size: 12px;
  flex-shrink: 0;
}

.tool-summary {
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;
  transition: color 0.2s;

  &:hover {
    color: #90138b;
  }
}

// 工具描述 Tooltip（聊天消息内）
// 移除原有的 tool-desc-tooltip 样式

// 消息底部区域：复制按钮 + 时间
.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px; // 增加与文字的间距
  padding-left: 11px; // 左侧内边距改为 11px，使 28px 宽的按钮图标中心对齐 16px 的文本
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
      color: #90138b;
    }
  }
}
</style>
